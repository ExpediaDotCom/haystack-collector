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

package com.expedia.www.haystack.kinesis.span.collector.pipeline

import java.util

import com.expedia.www.haystack.collector.commons.config.{ExternalKafkaConfiguration, ExtractorConfiguration, KafkaProduceConfiguration}
import com.expedia.www.haystack.collector.commons.sink.kafka.KafkaRecordSink
import com.expedia.www.haystack.collector.commons.{MetricsSupport, ProtoSpanExtractor}
import com.expedia.www.haystack.kinesis.span.collector.config.entities.KinesisConsumerConfiguration
import com.expedia.www.haystack.kinesis.span.collector.kinesis.client.KinesisConsumer
import com.expedia.www.haystack.span.decorators.plugin.config.{Plugin, PluginConfiguration}
import com.expedia.www.haystack.span.decorators.plugin.loader.SpanDecoratorPluginLoader
import com.expedia.www.haystack.span.decorators.{AdditionalTagsSpanDecorator, SpanDecorator}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try

class KinesisToKafkaPipeline(kafkaProducerConfig: KafkaProduceConfiguration,
                             listExternalKafkaConfig: List[ExternalKafkaConfiguration],
                             kinesisConsumerConfig: KinesisConsumerConfiguration,
                             extractorConfiguration: ExtractorConfiguration,
                             additionalTagsConfig: Map[String, String],
                             pluginConfig: Plugin
                            )
  extends AutoCloseable with MetricsSupport {

  private val LOGGER = LoggerFactory.getLogger(classOf[KinesisToKafkaPipeline])

  private var kafkaSink: KafkaRecordSink = _
  private var consumer: KinesisConsumer = _
  private var listSpanDecorator: List[SpanDecorator] = List()

  /**
    * run the pipeline. start the kinesis consumer worker and produce the read spans to kafka
    * the run is a blocking call. kinesis consumer blocks after spinning off the workers
    */
  def run(): Unit = {
    listSpanDecorator = getSpanDecoratorList()
    kafkaSink = new KafkaRecordSink(kafkaProducerConfig, listExternalKafkaConfig)
    consumer = new KinesisConsumer(kinesisConsumerConfig, new ProtoSpanExtractor(extractorConfiguration, LoggerFactory.getLogger(classOf[ProtoSpanExtractor]), listSpanDecorator), kafkaSink)
    consumer.startWorker()
  }

  private def getSpanDecoratorList(): List[SpanDecorator] = {
    var tempList: List[SpanDecorator] = List()
    if (pluginConfig != null) {
      val externalSpanDecorators: List[SpanDecorator] = SpanDecoratorPluginLoader.getInstance(LOGGER, pluginConfig).getSpanDecorators().asScala.toList
      if (externalSpanDecorators != null) {
        tempList = tempList.++:(externalSpanDecorators)
      }
    }


    val additionalTagsSpanDecorator = new AdditionalTagsSpanDecorator(additionalTagsConfig.asJava, LOGGER)
    tempList.::(additionalTagsSpanDecorator)
  }

  override def close(): Unit = {
    Try(consumer.close())
    Try(kafkaSink.close())
  }
}
