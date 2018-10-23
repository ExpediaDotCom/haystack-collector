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

package com.expedia.www.haystack.collector.commons.config

import java.io.File
import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig.{KEY_SERIALIZER_CLASS_CONFIG, VALUE_SERIALIZER_CLASS_CONFIG}
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.collection.JavaConversions._

object ConfigurationLoader {

  private val ENV_NAME_PREFIX = "HAYSTACK_PROP_"

  /**
    * Load and return the configuration
    * if overrides_config_path env variable exists, then we load that config file and use base conf as fallback,
    * else we load the config from env variables(prefixed with haystack) and use base conf as fallback
    *
    */
  lazy val loadAppConfig: Config = {
    val baseConfig = ConfigFactory.load("config/base.conf")

    sys.env.get("HAYSTACK_OVERRIDES_CONFIG_PATH") match {
      case Some(path) => ConfigFactory.parseFile(new File(path)).withFallback(baseConfig)
      case _ => loadFromEnvVars().withFallback(baseConfig)
    }
  }

  /**
    * @return new config object with haystack specific environment variables
    */
  private def loadFromEnvVars(): Config = {
    val envMap = sys.env.filter {
      case (envName, _) => isHaystackEnvVar(envName)
    } map {
      case (envName, envValue) => (transformEnvVarName(envName), envValue)
    }

    ConfigFactory.parseMap(envMap)
  }

  private def isHaystackEnvVar(env: String): Boolean = env.startsWith(ENV_NAME_PREFIX)

  /**
    * converts the env variable to HOCON format
    * for e.g. env variable HAYSTACK_KAFKA_STREAMS_NUM_STREAM_THREADS gets converted to kafka.streams.num.stream.threads
    * @param env environment variable name
    * @return
    */
  private def transformEnvVarName(env: String): String = {
    env.replaceFirst(ENV_NAME_PREFIX, "").toLowerCase.replace("_", ".")
  }

  def kafkaProducerConfig(config: Config): KafkaProduceConfiguration = {
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

  def extractorConfiguration(config: Config): ExtractorConfiguration = {
    val extractor = config.getConfig("extractor")
    ExtractorConfiguration(outputFormat = if (extractor.hasPath("output.format")) Format.withName(extractor.getString("output.format")) else Format.PROTO)

  }
}
