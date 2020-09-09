package com.inovatrend.akka.streams

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._

case class AppConfig(kafkaBootstrapServers: String,
                     kafkaTopicName: String,
                     esHosts: Seq[String],
                     esPort: Int,
                     esIndexName: String,
                     cassandraTableName: String,
                     producerTotalRecords: Int,
                     producerRecordDelayMs: Int,
                     testingOption: Int)

case class SmsMessage(id: String, from: String, to: String, text: String, timestamp: Long)


object AkkaStreamsDemoMain {

  protected val log: Logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.load()

    // Create an Akka system
    implicit val system: ActorSystem = ActorSystem("akka-streams-test", config)

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val appConfig = AppConfig(
      config.getString("app.kafkaBootstrapServers"),
      config.getString("app.kafkaTopicName"),
      config.getStringList("app.esHosts").asScala.toList,
      config.getInt("app.esPort"),
      config.getString("app.esIndexName"),
      config.getString("app.cassandraTableName"),
      config.getInt("app.producerTotalRecords"),
      config.getInt("app.producerRecordDelayMs"),
      config.getInt("app.testingOption")
    )

    system.actorOf(JsonEventsStreamActor.props(appConfig), JsonEventsStreamActor.ACTOR_NAME)
    system.actorOf(JsonKafkaProducerActor.props(appConfig), JsonKafkaProducerActor.ACTOR_NAME)

    log.info(s"Started Akka Streams test app")

    Await.ready(Future.never, Duration.Inf)

  }

}

