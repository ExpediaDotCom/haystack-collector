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

package com.expedia.www.haystack.kinesis.span.collector

import com.codahale.metrics.JmxReporter
import com.expedia.www.haystack.kinesis.span.collector.config.ProjectConfiguration
import com.expedia.www.haystack.kinesis.span.collector.metrics.MetricsSupport
import com.expedia.www.haystack.kinesis.span.collector.pipeline.KinesisToKafkaPipeline
import org.slf4j.LoggerFactory

object App extends MetricsSupport {
  private val LOGGER = LoggerFactory.getLogger(App.getClass)

  private var pipeline: KinesisToKafkaPipeline = _

  def main(args: Array[String]): Unit = {
    startJmxReporter()

    import ProjectConfiguration._
    try {
      addShutdownHook()
      pipeline = new KinesisToKafkaPipeline(kafkaProducerConfig(), kinesisConsumerConfig(),extractorConfiguration())
      pipeline.run()
    } catch {
      case ex: Exception =>
        LOGGER.error("Observed fatal exception while running the app", ex)
        if(pipeline != null) pipeline.close()
        System.exit(1)
    }
  }

  private def addShutdownHook(): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        LOGGER.info("Shutdown hook is invoked, tearing down the application.")
        if(pipeline != null) pipeline.close()
      }
    })
  }
  private def startJmxReporter() = {
    val jmxReporter = JmxReporter.forRegistry(metricRegistry).build()
    jmxReporter.start()
  }
}
