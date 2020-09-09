package com.inovatrend.akka.streams

import java.time.Instant

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{CommitterSettings, ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.alpakka.cassandra.scaladsl.{CassandraFlow, CassandraSession, CassandraSessionRegistry}
import akka.stream.alpakka.cassandra.{CassandraSessionSettings, CassandraWriteSettings}
import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchFlow
import akka.stream.alpakka.elasticsearch.{ApiVersion, ElasticsearchWriteSettings, WriteMessage}
import akka.stream.scaladsl.{Flow, RestartFlow, RestartSource, Sink}
import akka.{Done, NotUsed}
import com.datastax.oss.driver.api.core.cql.{BoundStatement, PreparedStatement}
import io.circe.generic.auto._
import io.circe.jawn.decode
import org.apache.http.HttpHost
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.elasticsearch.client.RestClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class EventsStreamContext(sms: SmsMessage, committableOffset: ConsumerMessage.CommittableOffset, jsonMessage: String)

object JsonEventsStreamActor {
  def props(appConfig: AppConfig): Props = Props(new JsonEventsStreamActor(appConfig))

  val ACTOR_NAME: String = "JsonEventsStreamActor"

}

class JsonEventsStreamActor(val appConfig: AppConfig) extends Actor with ActorLogging {

  implicit val actorSystem: ActorSystem = context.system

  context.system.scheduler.scheduleOnce(2 seconds, self, "start")

  def startStream(): Unit = {
    log.info(s"Starting JsonEventsStream")

    val committerSettings = CommitterSettings(context.system)

    val consumerSettings: ConsumerSettings[String, String] = {
      ConsumerSettings(context.system, new StringDeserializer, new StringDeserializer)
        .withBootstrapServers(appConfig.kafkaBootstrapServers)
        .withGroupId("akka-streams-test")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }


      appConfig.testingOption match {
        case 1 =>
          Consumer.committableSource(consumerSettings, Subscriptions.topics(appConfig.kafkaTopicName))
            .via(converter)
            .via(elasticFlow)
            .via(cassandraFlow)
            .to(Committer.sink(committerSettings))
            .run()
        case 2 =>
          RestartSource.onFailuresWithBackoff(10.seconds, 20.seconds, 0.2) { () =>
            Consumer.committableSource(consumerSettings, Subscriptions.topics(appConfig.kafkaTopicName))
          }
            .via(converter)
            .via(RestartFlow.onFailuresWithBackoff(5.seconds, 10.seconds, 0.2, 100)(() => elasticFlow.via(cassandraFlow)))
            .to(Committer.sink(committerSettings))
            .run()
        case 3 =>
          RestartSource.onFailuresWithBackoff(10.seconds, 20.seconds, randomFactor = 0.5) { () =>
            Consumer.committableSource(consumerSettings, Subscriptions.topics(appConfig.kafkaTopicName))
              .via(converter)
              .via(elasticFlow)
              .via(cassandraFlow)
              .via(Committer.flow(committerSettings))
          }
            .to(End)
            .run()
        case other => log.error(s"Unknown testingOption: $other")

      }

    /*
        // Option 2 - other implementation
        val option2Stream = RestartSource.onFailuresWithBackoff(10.seconds, 20.seconds, 0.2) { () =>
          val source: Source[CommittableMessage[String, String], Consumer.Control] = Consumer.committableSource(consumerSettings, Subscriptions.topics(appConfig.kafkaTopicName))
          source.mapMaterializedValue(c => control.set(c))
        }
          .via(converter)
          .via(RestartFlow.onFailuresWithBackoff(5.seconds, 10.seconds, 0.2, 100)(() => elasticFlow.via(cassandraFlow).via(Committer.flow(committerSettings))))
          .to(End)
    */


  }

  override def receive: Receive = {
    case "start" =>
      startStream()
    case otherMsg =>
      log.warning(s"Received unexpected msg: $otherMsg")
  }

  val converter: Flow[CommittableMessage[String, String], EventsStreamContext, NotUsed] = Flow[CommittableMessage[String, String]]
    .map[EventsStreamContext] { msg =>
      val sms = decode[SmsMessage](msg.record.value()).toOption.get
      val context = EventsStreamContext(sms, msg.committableOffset, msg.record.value())
      log.debug(s"Received $context")
      context
    }

  //************************************************************************
  //*
  //*  CASSANDRA
  //*
  //************************************************************************

  val sessionSettings: CassandraSessionSettings = CassandraSessionSettings()
  implicit val cassandraSession: CassandraSession = CassandraSessionRegistry.get(context.system).sessionFor(sessionSettings)

  val cassandraCqlStatement: String = s"INSERT INTO ${appConfig.cassandraTableName} (id, source, destination, sms_text, timestamp) VALUES (?, ?, ?, ?, ?)"

  val statementBinder: (EventsStreamContext, PreparedStatement) => BoundStatement = (record: EventsStreamContext, statement: PreparedStatement) => {
    val timestamp = Instant.ofEpochMilli(record.sms.timestamp)
    statement.bind(record.sms.id, record.sms.from, record.sms.to, record.sms.text, timestamp)
  }

  val cassandraFlow: Flow[EventsStreamContext, ConsumerMessage.CommittableOffset, NotUsed] =
    CassandraFlow.create[EventsStreamContext](CassandraWriteSettings.defaults, cassandraCqlStatement, statementBinder)
      .map { streamContext => streamContext.committableOffset }

  //************************************************************************
  //*
  //*  ELASTIC
  //*
  //************************************************************************

  implicit val client: RestClient = RestClient.builder(appConfig.esHosts.map(h => new HttpHost(h, appConfig.esPort, "http")): _*).build()

  val elasticFlow: Flow[EventsStreamContext, EventsStreamContext, NotUsed] = Flow[EventsStreamContext]
    .map { message =>
      val documentId = message.sms.id
      WriteMessage.createUpsertMessage(documentId, message.jsonMessage)
        .withIndexName(appConfig.esIndexName)
        .withPassThrough[EventsStreamContext](message)
    }
    .via(ElasticsearchFlow.createWithPassThrough[String, EventsStreamContext]
      ("", "", ElasticsearchWriteSettings().withApiVersion(ApiVersion.V7), (message: String) => message)
    )
    .map { item =>
      val event = item.message.source
      log.debug(s"esPassThrough: success=${item.success} event=$event")
      item.message.passThrough
    }

  //************************************************************************
  //*
  //*  SINK
  //*
  //************************************************************************
  val End: Sink[Any, Future[Done]] = Sink.ignore


  /*
    val esWriter: Flow[EventsStreamContext, WriteMessage[String, EventsStreamContext], NotUsed] = Flow[EventsStreamContext].map { message =>
      val documentId = message.sms.id
      WriteMessage.createUpsertMessage(documentId, message.jsonMessage)
        .withIndexName(eventsConfig.esIndexName)
        .withPassThrough[EventsStreamContext](message)
    }

    val esPassThrough: Flow[WriteMessage[String, EventsStreamContext], WriteResult[String, EventsStreamContext], NotUsed] = ElasticsearchFlow.createWithPassThrough[String, EventsStreamContext](
      "", "", ElasticsearchWriteSettings().withApiVersion(ApiVersion.V7), (message: String) => message
    )

    val esResultParser: Flow[WriteResult[String, EventsStreamContext], EventsStreamContext, NotUsed] = Flow[WriteResult[String, EventsStreamContext]].map { item =>
      val event = item.message.source
      log.debug(s"esPassThrough: success=${item.success} event=$event")
      item.message.passThrough
    }
  */

  //  val elasticFlow: Flow[EventsStreamContext, EventsStreamContext, NotUsed] = Flow[EventsStreamContext]
  //    .via(esWriter)
  //    .via(esPassThrough)
  //    .via(esResultParser)


}