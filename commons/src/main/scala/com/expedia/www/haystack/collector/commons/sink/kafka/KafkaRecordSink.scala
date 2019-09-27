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

import java.util.Properties

import com.codahale.metrics.Meter
import com.expedia.open.tracing.{Span, Tag}
import com.expedia.www.haystack.collector.commons.MetricsSupport
import com.expedia.www.haystack.collector.commons.config.{ExternalKafkaConfiguration, KafkaProduceConfiguration}
import com.expedia.www.haystack.collector.commons.record.KeyValuePair
import com.expedia.www.haystack.collector.commons.sink.RecordSink
import com.expedia.www.haystack.collector.commons.sink.metrics.KafkaMetricNames
import org.apache.kafka.clients.producer.{ProducerRecord, _}
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class KafkaRecordSink(config: KafkaProduceConfiguration,
                      additionalKafkaProducerConfigs: List[ExternalKafkaConfiguration]) extends RecordSink with MetricsSupport {

  private val LOGGER = LoggerFactory.getLogger(classOf[KafkaRecordSink])

  private val defaultProducer: (Meter, KafkaProducer[Array[Byte], Array[Byte]]) = getDefaultProducerWithMeter(config)
  private val additionalProducers: List[(Meter, KafkaProducers)] = getAdditionalProducersWithMeter(additionalKafkaProducerConfigs)

  override def toAsync(kvPair: KeyValuePair[Array[Byte], Array[Byte]],
                       callback: (KeyValuePair[Array[Byte], Array[Byte]], Exception) => Unit = null): Unit = {
    val kafkaMessage = new ProducerRecord(config.topic, kvPair.key, kvPair.value)

    defaultProducer._2.send(kafkaMessage, new Callback {
      override def onCompletion(recordMetadata: RecordMetadata, e: Exception): Unit = {
        if (e != null) {
          LOGGER.error(s"Fail to produce the message to kafka for topic=${config.topic} with reason", e)
        } else {
          defaultProducer._1.mark()
        }
        if(callback != null) callback(kvPair, e)
      }
    })

    getMatchingProducers(additionalProducers, Span.parseFrom(kvPair.value)).foreach(p => {
      val tempKafkaMessage = new ProducerRecord(p._2.topic, kvPair.key, kvPair.value)
      p._2.producer.send(tempKafkaMessage, new Callback {
        override def onCompletion(recordMetadata: RecordMetadata, e: Exception): Unit = {
          if (e != null) {
            LOGGER.error(s"Fail to produce the message to kafka for topic=${p._2.topic} with reason", e)
          } else {
            p._1.mark()
          }
          if(callback != null) callback(kvPair, e)
        }
      })
    })
  }

  private def getMatchingProducers(producers: List[(Meter, KafkaProducers)], span: Span): List[(Meter, KafkaProducers)] = {
    val tagList: List[Tag] = span.getTagsList.asScala.toList
    producers.filter(producer => producer._2.isMatched(tagList))
  }

  override def close(): Unit = {
    if(defaultProducer != null) {
      defaultProducer._2.flush()
      defaultProducer._2.close()
    }
    additionalProducers.foreach(p => p._2.close())
  }

  private def getDefaultProducerWithMeter(config: KafkaProduceConfiguration): (Meter, KafkaProducer[Array[Byte], Array[Byte]]) = {
    val kafkaWriteRateMeter = metricRegistry.meter(KafkaMetricNames.kafkaWriteRate + ".default.cluster")
    (kafkaWriteRateMeter, new KafkaProducer[Array[Byte], Array[Byte]](config.props))
  }

  private def getAdditionalProducersWithMeter(additionalKafkaProducerConfigs: List[ExternalKafkaConfiguration]): List[(Meter, KafkaProducers)] = {
    additionalKafkaProducerConfigs
      .map(cfg => {
        (metricRegistry.meter(KafkaMetricNames + "." + cfg.name),
          KafkaProducers(cfg.name,
            cfg.tags,
            cfg.kafkaProduceConfiguration.topic,
            new KafkaProducer[Array[Byte], Array[Byte]](cfg.kafkaProduceConfiguration.props)))
      })
  }

  case class KafkaProducers(name: String, tags: Map[String, String], topic: String, producer: KafkaProducer[Array[Byte], Array[Byte]]) {
    def isMatched(spanTags: List[Tag]): Boolean = {
      val filteredTags = spanTags.filter(t => t.getVStr.equals(tags.getOrElse(t.getKey, null)))
      filteredTags.size.equals(tags.size)
    }

    def close(): Unit = {
      producer.flush()
      producer.close()
    }
  }
}
