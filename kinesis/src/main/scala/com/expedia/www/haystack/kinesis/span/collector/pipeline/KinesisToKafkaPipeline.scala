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

import com.expedia.www.haystack.kinesis.span.collector.config.entities.{ExtractorConfiguration, KafkaProduceConfiguration, KinesisConsumerConfiguration}
import com.expedia.www.haystack.kinesis.span.collector.kinesis.client.KinesisConsumer
import com.expedia.www.haystack.kinesis.span.collector.kinesis.record.ProtoSpanExtractor
import com.expedia.www.haystack.kinesis.span.collector.sink.kafka.KafkaRecordSink

import scala.util.Try

class KinesisToKafkaPipeline(kafkaProducerConfig: KafkaProduceConfiguration, kinesisConsumerConfig: KinesisConsumerConfiguration,extractorConfiguration: ExtractorConfiguration)
  extends AutoCloseable{

  private var kafkaSink: KafkaRecordSink = _
  private var consumer: KinesisConsumer = _

  /**
    * run the pipeline. start the kinesis consumer worker and produce the read spans to kafka
    * the run is a blocking call. kinesis consumer blocks after spinning off the workers
    */
  def run(): Unit = {
    kafkaSink = new KafkaRecordSink(kafkaProducerConfig)
    consumer = new KinesisConsumer(kinesisConsumerConfig,new ProtoSpanExtractor(extractorConfiguration), kafkaSink)
    consumer.startWorker()
  }

  override def close(): Unit = {
    Try(consumer.close())
    Try(kafkaSink.close())
  }
}
