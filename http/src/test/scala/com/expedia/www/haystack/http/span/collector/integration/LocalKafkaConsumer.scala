/*
 *  Copyright 2018 Expedia, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.expedia.www.haystack.http.span.collector.integration

import java.util.Properties

import com.expedia.www.haystack.http.span.collector.integration.config.TestConfiguration
import org.apache.kafka.clients.consumer.internals.NoOpConsumerRebalanceListener
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._

trait LocalKafkaConsumer {

  private val kafkaConsumer = {
    val consumerProperties = new Properties()
    consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "http-to-kafka-test")
    consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestConfiguration.remoteKafkaHost + ":" + TestConfiguration.kafkaPort)
    consumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getCanonicalName)
    consumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getCanonicalName)
    new KafkaConsumer[Array[Byte], Array[Byte]](consumerProperties)
  }

  kafkaConsumer.subscribe(List(TestConfiguration.kafkaStreamName).asJava, new NoOpConsumerRebalanceListener())

  def readRecordsFromKafka(minExpectedCount: Int, maxWait: FiniteDuration): List[Array[Byte]] = {
    val records = mutable.ListBuffer[Array[Byte]]()
    var received: Int = 0

    var waitTimeLeft = maxWait.toMillis
    var done = true
    while (done) {
      kafkaConsumer.poll(250).records(TestConfiguration.kafkaStreamName).map(rec => {
        received += 1
        records += rec.value()
      })
      if(received < minExpectedCount && waitTimeLeft > 0) {
        Thread.sleep(1000)
        waitTimeLeft -= 1000
      } else {
        done = false
      }
    }

    if(records.size < minExpectedCount) throw new RuntimeException("Fail to read the expected records from kafka")

    records.toList
  }

  def shutdownKafkaConsumer(): Unit = {
    if(kafkaConsumer != null) kafkaConsumer.close()
  }
}
