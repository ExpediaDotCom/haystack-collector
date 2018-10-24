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

package com.expedia.www.haystack.http.span.collector

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Route}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.codahale.metrics.JmxReporter
import com.expedia.www.haystack.collector.commons.sink.kafka.KafkaRecordSink
import com.expedia.www.haystack.collector.commons.{MetricsSupport, ProtoSpanExtractor}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.sys._

object WebServer extends App with MetricsSupport {
  val LOGGER = LoggerFactory.getLogger(WebServer.getClass)

  // setup kafka sink
  private val kafkaSink = new KafkaRecordSink(ProjectConfiguration.kafkaProducerConfig())
  private val kvExtractor = new ProtoSpanExtractor(ProjectConfiguration.extractorConfig(), LoggerFactory.getLogger(classOf[ProtoSpanExtractor]))
  private val http = ProjectConfiguration.httpConfig()

  // setup actor system
  implicit val system: ActorSystem = ActorSystem("span-collector", ProjectConfiguration.config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  // start jmx reporter
  private val jmxReporter = JmxReporter.forRegistry(metricRegistry).build()
  jmxReporter.start()

  // start http server on given host and port
  val bindingFuture = Http(system).bindAndHandle(routes(), http.host, http.port)
  LOGGER.info(s"Server is now listening at http://${http.host}:${http.port}")

  addShutdownHook { shutdownHook() }

  def processSpan(entity: RequestEntity): Future[StatusCode] = {
    entity
      .dataBytes
      .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
      .map(_.compact.toArray[Byte])
      .map(kvExtractor.extractKeyValuePairs)
      .map(kvPairs => {
        kvPairs foreach { kv => kafkaSink.toAsync(kv) }
        StatusCode.int2StatusCode(StatusCodes.Accepted.intValue)
      })
  }

  def routes(): Route = {
    // build the routes
    path("span") {
      post {
        extractRequest {
          req =>
            if (http.authenticator(req)) {
              complete {
                processSpan(req.entity)
              }
            } else {
              reject(AuthorizationFailedRejection)
            }
        }
      }
    } ~
      path("isActive") {
        get {
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ACTIVE"))
        }
      }
  }

  def shutdownHook(): Unit = {
    LOGGER.info("Terminating Server ...")
    bindingFuture
      .flatMap(_.unbind())
      .onComplete { _ => close() }
    Await.result(system.whenTerminated, 30.seconds)
  }

  def close(): Unit = {
    kafkaSink.close()
    materializer.shutdown()
    system.terminate()
    jmxReporter.close()
  }
}