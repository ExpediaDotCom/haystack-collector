/*
 *  Copyright 2017 Expedia, Inc.
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

package com.expedia.www.haystack.collector.commons.sink.kafka

import com.expedia.www.haystack.collector.commons.MetricsSupport
import com.expedia.www.haystack.collector.commons.config.{ExternalKafkaConfiguration, KafkaProduceConfiguration}
import com.expedia.www.haystack.collector.commons.record.KeyValuePair
import com.expedia.www.haystack.collector.commons.sink.RecordSink
import org.apache.kafka.clients.producer.{ProducerRecord, _}
import org.slf4j.LoggerFactory

class KafkaRecordSink(config: KafkaProduceConfiguration, listExternalKafkaConfig: List[ExternalKafkaConfiguration]) extends RecordSink with MetricsSupport {

  private val LOGGER = LoggerFactory.getLogger(classOf[KafkaRecordSink])

  private val defaultProducer: KafkaProducer[Array[Byte], Array[Byte]] = new KafkaProducer[Array[Byte], Array[Byte]](config.props)
  private val listExternalProducer: Map[List[(String, String)], KafkaProducer[Array[Byte], Array[Byte]]] = listExternalKafkaConfig
    .map(cfg => {
      cfg.tags.toList -> new KafkaProducer[Array[Byte], Array[Byte]](cfg.kafkaProduceConfiguration.props)
    }).toMap

  override def toAsync(kvPair: KeyValuePair[Array[Byte], Array[Byte]],
                       callback: (KeyValuePair[Array[Byte], Array[Byte]], Exception) => Unit = null): Unit = {
    val kafkaMessage = new ProducerRecord(config.topic, kvPair.key, kvPair.value)

    defaultProducer.send(kafkaMessage, new Callback {
      override def onCompletion(recordMetadata: RecordMetadata, e: Exception): Unit = {
        if (e != null) {
          LOGGER.error(s"Fail to produce the message to kafka for topic=${config.topic} with reason", e)
        }
        if(callback != null) callback(kvPair, e)
      }
    })
  }

  override def close(): Unit = {
    if(defaultProducer != null) {
      defaultProducer.flush()
      defaultProducer.close()
    }
  }
}
