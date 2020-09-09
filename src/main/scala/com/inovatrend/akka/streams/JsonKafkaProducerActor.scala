package com.inovatrend.akka.streams

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import akka.kafka.ProducerSettings
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.commons.lang3.RandomStringUtils
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt


object JsonKafkaProducerActor {
  def props(appConfig: AppConfig): Props = Props(new JsonKafkaProducerActor(appConfig))

  val ACTOR_NAME: String = "JsonKafkaProducerActor"
}

case class SendJsonMessageToKafka(topic: String, message: String)

class JsonKafkaProducerActor(appConfig: AppConfig) extends Actor with ActorLogging {

  val config: Config = context.system.settings.config.getConfig("akka.kafka.producer")
  val counter: AtomicLong = new AtomicLong(0)

  val producerSettings: ProducerSettings[String, String] =
    ProducerSettings(config, new StringSerializer, new StringSerializer)
      .withBootstrapServers(appConfig.kafkaBootstrapServers)

  var kafkaProducer: Option[Producer[String, String]] = Option.empty[Producer[String, String]]

  private val scheduled: Cancellable = context.system.scheduler.scheduleWithFixedDelay(5.seconds, appConfig.producerRecordDelayMs.milliseconds, self, "send")

  override def preStart(): Unit = {
    log.info(s"Starting actor: ${self.path.name}")
    kafkaProducer = Some(producerSettings.createKafkaProducer())
    super.preStart()
  }

  override def postStop(): Unit = {
    kafkaProducer.foreach(producer => {
      log.info("Stopping kafka producer")
      producer.flush()
      producer.close()
    })
    super.postStop()
  }

  override def receive: Receive = {
    case "send" =>
      val currentCount = counter.get()
      if (currentCount >= appConfig.producerTotalRecords) {
        log.info(s"Finished sending configured amount of records, killing myself")
        scheduled.cancel()
        context.stop(self)
      } else {
        val sms = SmsMessage(RandomStringUtils.randomNumeric(10), RandomStringUtils.randomNumeric(12), RandomStringUtils.randomNumeric(12), RandomStringUtils.randomAlphabetic(20), System.currentTimeMillis())
        log.debug(s"Producing: $sms")
        kafkaProducer.get.send(new ProducerRecord(appConfig.kafkaTopicName, sms.asJson.noSpaces))
        counter.incrementAndGet()
      }
    case msg => log.warning(s"Unknown message received. msg=$msg")
  }
}
