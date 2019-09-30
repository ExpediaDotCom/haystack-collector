package com.expedia.www.haystack.collector.commons.integration

import java.util.Properties

import com.expedia.open.tracing.Span
import com.expedia.www.haystack.collector.commons.config.KafkaProduceConfiguration
import com.expedia.www.haystack.collector.commons.record.KeyValuePair
import com.expedia.www.haystack.collector.commons.sink.kafka.KafkaRecordSink
import com.google.common.util.concurrent.RateLimiter
import org.apache.kafka.clients.producer.ProducerConfig.{KEY_SERIALIZER_CLASS_CONFIG, VALUE_SERIALIZER_CLASS_CONFIG}
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Matchers, OptionValues, WordSpec}

import scala.concurrent.duration._

class KafkaSinkIntegrationSpec extends WordSpec with GivenWhenThen with Matchers with LocalKafkaConsumer
  with OptionValues with BeforeAndAfterAll {
  private val StartTimeMicros = System.currentTimeMillis() * 1000
  private val DurationMicros = 42

  override def beforeAll(): Unit = {
  }

  override def afterAll(): Unit = {
  }

  "Kafka Sink" should {
    "not allow spans beyond throttled limit to be written" in {

      Given("a valid span and a kafka sink")
      val properties = new Properties()
      properties.setProperty("bootstrap.servers", TestConfiguration.remoteKafkaHost + ":" + TestConfiguration.kafkaPort)
      properties.put(KEY_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getCanonicalName)
      properties.put(VALUE_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getCanonicalName)
      val kafkaProduceConfiguration = KafkaProduceConfiguration(TestConfiguration.kafkaStreamName, properties)
      val kafkaSink = new KafkaRecordSink(kafkaProduceConfiguration, List(), RateLimiter.create(5))
      val span_1 = Span.newBuilder()
        .setSpanId("span-id-1")
        .setTraceId("trace-id-1")
        .setServiceName("service")
        .setOperationName("operation")
        .setStartTime(StartTimeMicros)
        .setDuration(DurationMicros)
        .build()

      When("the span is sent to Kafka sink 10 times")
      for (i <- 1 to 10) {
        kafkaSink.toAsync(KeyValuePair("trace-id-1".getBytes("UTF-8"), span_1.toByteArray()), null)
      }


      Then("it should be pushed to kafka only 5 times")
      val records = readRecordsFromKafka(5, 1.second).headOption
      records.size shouldEqual (5)
    }
  }
}
