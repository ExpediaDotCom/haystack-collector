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

package com.expedia.www.haystack.kinesis.span.collector.config

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream
import com.amazonaws.services.kinesis.metrics.interfaces.MetricsLevel
import com.expedia.www.haystack.collector.commons.config.ConfigurationLoader
import com.expedia.www.haystack.kinesis.span.collector.config.entities.{ExtractorConfiguration, Format, KafkaProduceConfiguration, KinesisConsumerConfiguration}
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig.{KEY_SERIALIZER_CLASS_CONFIG, VALUE_SERIALIZER_CLASS_CONFIG}
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.collection.JavaConversions._
import scala.concurrent.duration._

object ProjectConfiguration {

  private val config = ConfigurationLoader.loadAppConfig

  def healthStatusFile(): Option[String] = if(config.hasPath("health.status.path")) Some(config.getString("health.status.path")) else None

  def kafkaProducerConfig(): KafkaProduceConfiguration = {
    val props = new Properties()

    val kafka = config.getConfig("kafka.producer")

    kafka.getConfig("props").entrySet() foreach {
      kv => {
        props.setProperty(kv.getKey, kv.getValue.unwrapped().toString)
      }
    }

    props.put(KEY_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getCanonicalName)
    props.put(VALUE_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getCanonicalName)

    val produceTopic = kafka.getString("topic")

    // verify if at least bootstrap server config is set
    require(props.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG).nonEmpty)
    require(produceTopic.nonEmpty)

    KafkaProduceConfiguration(produceTopic, props)
  }

  def extractorConfiguration(): ExtractorConfiguration = {
    val extractor = config.getConfig("extractor")
    ExtractorConfiguration(outputFormat = if (extractor.hasPath("output.format")) Format.withName(extractor.getString("output.format")) else Format.PROTO)

  }

  def kinesisConsumerConfig(): KinesisConsumerConfiguration = {
    val kinesis = config.getConfig("kinesis")
    val stsRoleArn = if(kinesis.hasPath("sts.role.arn")) Some(kinesis.getString("sts.role.arn")) else None

    KinesisConsumerConfiguration(
      awsRegion = kinesis.getString("aws.region"),
      stsRoleArn = stsRoleArn,
      appGroupName = kinesis.getString("app.group.name"),
      streamName = kinesis.getString("stream.name"),
      streamPosition =InitialPositionInStream.valueOf(kinesis.getString("stream.position")),
      kinesis.getDuration("checkpoint.interval.ms", TimeUnit.MILLISECONDS).millis,
      kinesis.getInt("checkpoint.retries"),
      kinesis.getDuration("checkpoint.retry.interval.ms", TimeUnit.MILLISECONDS).millis,
      kinesisEndpoint = if (kinesis.hasPath("endpoint")) Some(kinesis.getString("endpoint")) else None,
      dynamoEndpoint = if (kinesis.hasPath("dynamodb.endpoint")) Some(kinesis.getString("dynamodb.endpoint")) else None,
      dynamoTableName = if(kinesis.hasPath("dynamodb.table")) Some(kinesis.getString("dynamodb.table")) else None,
      maxRecordsToRead = kinesis.getInt("max.records.read"),
      idleTimeBetweenReads = kinesis.getDuration("idle.time.between.reads.ms", TimeUnit.MILLISECONDS).millis,
      shardSyncInterval = kinesis.getDuration("shard.sync.interval.ms", TimeUnit.MILLISECONDS).millis,
      metricsLevel = MetricsLevel.fromName(kinesis.getString("metrics.level")),
      metricsBufferTime = kinesis.getDuration("metrics.buffer.time.ms", TimeUnit.MILLISECONDS).millis,
      taskBackoffTime = kinesis.getDuration("task.backoff.ms", TimeUnit.MILLISECONDS).millis)
  }
}
