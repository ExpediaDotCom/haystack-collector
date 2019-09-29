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

import com.expedia.www.haystack.collector.commons.config.{ExternalKafkaConfiguration, ExtractorConfiguration, KafkaProduceConfiguration, RateLimiterConfiguration}
import com.expedia.www.haystack.collector.commons.sink.kafka.KafkaRecordSink
import com.expedia.www.haystack.collector.commons.{MetricsSupport, ProtoSpanExtractor, SpanDecoratorFactory}
import com.expedia.www.haystack.kinesis.span.collector.config.entities.KinesisConsumerConfiguration
import com.expedia.www.haystack.kinesis.span.collector.kinesis.client.KinesisConsumer
import com.expedia.www.haystack.span.decorators.plugin.loader.SpanDecoratorPluginLoader
import com.expedia.www.haystack.span.decorators.plugin.config.{Plugin, PluginConfiguration}
import com.expedia.www.haystack.span.decorators.{AdditionalTagsSpanDecorator, SpanDecorator}
import com.google.common.util.concurrent.RateLimiter
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try

class KinesisToKafkaPipeline(kafkaProducerConfig: KafkaProduceConfiguration,
                             listExternalKafkaConfig: List[ExternalKafkaConfiguration],
                             kinesisConsumerConfig: KinesisConsumerConfiguration,
                             extractorConfiguration: ExtractorConfiguration,
                             additionalTagsConfig: Map[String, String],
                             rateLimiterConfig: RateLimiterConfiguration,
                             pluginConfig: Plugin
                            )
  extends AutoCloseable with MetricsSupport {

  private val LOGGER = LoggerFactory.getLogger(classOf[KinesisToKafkaPipeline])

  private var kafkaSink: KafkaRecordSink = _
  private var consumer: KinesisConsumer = _
  private var rateLimiter: RateLimiter = _
  private var listSpanDecorator: List[SpanDecorator] = List()

  /**
    * run the pipeline. start the kinesis consumer worker and produce the read spans to kafka
    * the run is a blocking call. kinesis consumer blocks after spinning off the workers
    */
  def run(): Unit = {
    rateLimiter = RateLimiter.create(rateLimiterConfig.throttleAt)
    listSpanDecorator = SpanDecoratorFactory.get(pluginConfig, additionalTagsConfig, LOGGER)
    kafkaSink = new KafkaRecordSink(kafkaProducerConfig, listExternalKafkaConfig, rateLimiter)
    consumer = new KinesisConsumer(kinesisConsumerConfig, new ProtoSpanExtractor(extractorConfiguration, LoggerFactory.getLogger(classOf[ProtoSpanExtractor]), listSpanDecorator), kafkaSink)
    consumer.startWorker()
  }

  override def close(): Unit = {
    Try(consumer.close())
    Try(kafkaSink.close())
  }
}
