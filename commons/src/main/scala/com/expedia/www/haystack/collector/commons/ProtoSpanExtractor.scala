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

package com.expedia.www.haystack.collector.commons

import java.nio.charset.Charset
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.HOURS

import com.expedia.open.tracing.Span
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.DurationIsInvalid
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.MaximumOperationNameCount
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.OperationNameIsRequired
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.ServiceNameIsRequired
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.ServiceNameVsTtlAndOperationNames
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.SmallestAllowedStartTimeMicros
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.SpanIdIsRequired
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.StartTimeIsInvalid
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.TraceIdIsRequired
import com.expedia.www.haystack.collector.commons.config.ExtractorConfiguration
import com.expedia.www.haystack.collector.commons.config.Format
import com.expedia.www.haystack.collector.commons.record.KeyValueExtractor
import com.expedia.www.haystack.collector.commons.record.KeyValuePair
import com.google.protobuf.util.JsonFormat
import org.slf4j.Logger

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object ProtoSpanExtractor {
  private val DaysInYear1970 = 365
  private val January_1_1971_00_00_00_GMT: Instant = Instant.EPOCH.plus(DaysInYear1970, ChronoUnit.DAYS)
  // A common mistake clients often make is to pass in milliseconds instead of microseconds for start time.
  // Insisting that all start times be > January 1 1971 GMT catches this error.
  val SmallestAllowedStartTimeMicros: Long = January_1_1971_00_00_00_GMT.getEpochSecond * 1000000
  val ServiceNameIsRequired = "Service Name is required: span=[%s]"
  val OperationNameIsRequired = "Operation Name is required: serviceName=[%s]"
  val SpanIdIsRequired = "Span ID is required: serviceName=[%s] operationName=[%s]"
  val TraceIdIsRequired = "Trace ID is required: serviceName=[%s] operationName=[%s]"
  val StartTimeIsInvalid = "Start time [%d] is invalid: serviceName=[%s] operationName=[%s]"
  val DurationIsInvalid = "Duration [%d] is invalid: serviceName=[%s] operationName=[%s]"

  val ServiceNameVsTtlAndOperationNames = new ConcurrentHashMap[String, TtlAndOperationNames]
  val MaximumOperationNameCount = 1000
}

class ProtoSpanExtractor(extractorConfiguration: ExtractorConfiguration,
                         val LOGGER: Logger) extends KeyValueExtractor with MetricsSupport {
  private val printer = JsonFormat.printer().omittingInsignificantWhitespace()

  private val invalidSpanMeter = metricRegistry.meter("invalid.span")
  private val validSpanMeter = metricRegistry.meter("valid.span")

  override def configure(): Unit = ()

  def validateServiceName(span: Span): Try[Span] = {
    validate(span, span.getServiceName, ServiceNameIsRequired, span.toString)
  }

  def validateOperationName(span: Span): Try[Span] = {
    validate(span, span.getOperationName, OperationNameIsRequired, span.getServiceName)
  }

  def validateSpanId(span: Span): Try[Span] = {
    validate(span, span.getSpanId, SpanIdIsRequired, span.getServiceName, span.getOperationName)
  }

  def validateTraceId(span: Span): Try[Span] = {
    validate(span, span.getTraceId, TraceIdIsRequired, span.getServiceName, span.getOperationName)
  }

  def validateStartTime(span: Span): Try[Span] = {
    validate(span, span.getStartTime, StartTimeIsInvalid, SmallestAllowedStartTimeMicros, span.getServiceName, span.getOperationName)
  }

  def validateDuration(span: Span): Try[Span] = {
    validate(span, span.getDuration, DurationIsInvalid, 0, span.getServiceName, span.getOperationName)
  }

  private def validate(span: Span,
                       valueToValidate: String,
                       msg: String,
                       serviceName: String): Try[Span] = {
    if (Option(valueToValidate).getOrElse("").isEmpty) {
      Failure(new IllegalArgumentException(msg.format(serviceName)))
    } else {
      Success(span)
    }
  }

  private def validate(span: Span,
                       valueToValidate: String,
                       msg: String,
                       serviceName: String,
                       operationName: String): Try[Span] = {
    if (Option(valueToValidate).getOrElse("").isEmpty) {
      Failure(new IllegalArgumentException(msg.format(serviceName, operationName)))
    } else {
      Success(span)
    }
  }

  private def validate(span: Span,
                       valueToValidate: Long,
                       msg: String,
                       smallestValidValue: Long,
                       serviceName: String,
                       operationName: String): Try[Span] = {
    if (valueToValidate < smallestValidValue) {
      Failure(new IllegalArgumentException(msg.format(valueToValidate, serviceName, operationName)))
    } else {
      Success(span)
    }
  }

  // TODO Emit a metric; see https://github.com/ExpediaDotCom/haystack-collector/issues/62
//  /**
//    * Validate that the operation name cardinality is "small enough." A large operation name count stresses other
//    * Haystack services; currently the count is maintained independently in each haystack-collector host instead of
//    * being stored in a distributed cache. A one hour TTL is maintained for each service; when a service sends an
//    * excessive number of operation names, the Set of operation names will fill up and cause spans to be rejected,
//    * logging an error and incrementing a counter with each rejection. (A counter is also incremented when a Span is
//    * parsed successfully.) When a Span is rejected, the TTL of the service name is examined; if it indicates that that
//    * TTL has been reached, then the entire set of operation names is cleared, with the expectation and hope that a new
//    * version of the service will have been deployed that sends fewer operation names. If this is not the case, the map
//    * will quickly fill up again, and the cycle will repeat, with spans from the offending service being rejected for
//    * the next hour.
//    *
//    * @param span Span that contains the service name and operation code to be examined
//    * @param currentTimeMillis current time, in milliseconds, exposed for easier unit testing
//    * @param ttlDurationMillis TTL of the operation code in the counting map, exposed for easier unit testing
//    * @return Success(span) if operation name count is acceptably low, Failure(IllegalArgumentException) otherwise
//    */
//  def validateOperationNameCount(span: Span,
//                                 currentTimeMillis: Long,
//                                 ttlDurationMillis: Long): Try[Span] = {
//    val newTtlMillis = currentTimeMillis + ttlDurationMillis
//    val ttlAndOperationNames = Option(ServiceNameVsTtlAndOperationNames.get(span.getServiceName))
//      .getOrElse(new TtlAndOperationNames(newTtlMillis))
//    if(ttlAndOperationNames.operationNames.size() <= MaximumOperationNameCount) {
//      ServiceNameVsTtlAndOperationNames.put(span.getServiceName, ttlAndOperationNames)
//      ttlAndOperationNames.operationNames.add(span.getOperationName)
//      ttlAndOperationNames.setTtlMillis(newTtlMillis)
//      Success(span)
//    } else {
//      if (ttlAndOperationNames.getTtlMillis <= currentTimeMillis) {
//        ttlAndOperationNames.operationNames.clear()
//      }
//      Failure(new IllegalArgumentException("Too many operation names: serviceName=[" + span.getServiceName + "]"))
//    }
//  }

  override def extractKeyValuePairs(recordBytes: Array[Byte]): List[KeyValuePair[Array[Byte], Array[Byte]]] = {
    Try(Span.parseFrom(recordBytes))
      .flatMap(span => validateServiceName(span))
      .flatMap(span => validateOperationName(span))
      .flatMap(span => validateSpanId(span))
      .flatMap(span => validateTraceId(span))
      .flatMap(span => validateStartTime(span))
      .flatMap(span => validateDuration(span))
      //.flatMap(span => validateOperationNameCount(span, System.currentTimeMillis(), HOURS.toMillis(1)))
    match {
      case Success(span) =>
        validSpanMeter.mark()
        val kvPair = extractorConfiguration.outputFormat match {
          case Format.JSON => KeyValuePair(span.getTraceId.getBytes, printer.print(span).getBytes(Charset.forName("UTF-8")))
          case Format.PROTO => KeyValuePair(span.getTraceId.getBytes, recordBytes)
        }
        List(kvPair)

      case Failure(ex) =>
        invalidSpanMeter.mark()
        ex match {
          case ex: IllegalArgumentException => LOGGER.error(ex.getMessage)
          case _: java.lang.Exception => LOGGER.error("Fail to deserialize the span proto bytes with exception", ex)
        }
        Nil
    }
  }
}
