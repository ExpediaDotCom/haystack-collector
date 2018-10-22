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

import com.expedia.open.tracing.Span
import com.expedia.www.haystack.collector.commons.config.{ExtractorConfiguration, Format}
import com.expedia.www.haystack.collector.commons.record.{KeyValueExtractor, KeyValuePair}
import com.google.protobuf.util.JsonFormat
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object ProtoSpanExtractor {
  private val DaysInYear1970 = 365
  private val January_1_1971_00_00_00_GMT: Instant = Instant.EPOCH.plus(DaysInYear1970, ChronoUnit.DAYS)
  // A common mistake clients often make is to pass in milliseconds instead of microseconds for start time.
  // Insisting that all start times be > January 1 1971 GMT catches this error.
  val SmallestAllowedStartTimeMicros: Long = January_1_1971_00_00_00_GMT.getEpochSecond * 1000000
}

class ProtoSpanExtractor(extractorConfiguration: ExtractorConfiguration) extends KeyValueExtractor {
  private val LOGGER = LoggerFactory.getLogger(classOf[ProtoSpanExtractor])
  private val printer = JsonFormat.printer().omittingInsignificantWhitespace()

  override def configure(): Unit = ()

  def validateSpanId(span: Span): Try[Span] = {
    validate(span, span.getSpanId, "Span ID is required: trace ID=%s", span.getTraceId)
  }

  def validateTraceId(span: Span): Try[Span] = {
    validate(span, span.getTraceId, "Trace ID is required: span ID=%s", span.getSpanId)
  }

  def validateServiceName(span: Span): Try[Span] = {
    validate(span, span.getServiceName, "Service Name is required: span ID=%s", span.getSpanId)
  }

  def validateOperationName(span: Span): Try[Span] = {
    validate(span, span.getOperationName, "Operation Name is required: span ID=%s", span.getSpanId)
  }

  def validateStartTime(span: Span): Try[Span] = {
    validate(span, span.getStartTime, "Start time is required: span ID=%s", span.getSpanId, ProtoSpanExtractor.SmallestAllowedStartTimeMicros)
  }

  def validateDuration(span: Span): Try[Span] = {
    validate(span, span.getDuration, "Duration is required: span ID=%s", span.getSpanId, 0)
  }

  private def validate(span: Span,
                       valueToValidate: String,
                       msg: String,
                       additionalInfoForMsg: String): Try[Span] = {
    if (Option(valueToValidate).getOrElse("").isEmpty) {
      Failure(new IllegalArgumentException(msg.format(additionalInfoForMsg)))
    } else {
      Success(span)
    }
  }

  private def validate(span: Span,
                       valueToValidate: Long,
                       msg: String,
                       additionalInfoForMsg: String,
                       smallestValidValue: Long): Try[Span] = {
    if (valueToValidate < smallestValidValue) {
      Failure(new IllegalArgumentException(msg.format(additionalInfoForMsg)))
    } else {
      Success(span)
    }
  }

  override def extractKeyValuePairs(recordBytes: Array[Byte]): List[KeyValuePair[Array[Byte], Array[Byte]]] = {
    Try(Span.parseFrom(recordBytes))
      .flatMap(span => validateSpanId(span))
      .flatMap(span => validateTraceId(span))
      .flatMap(span => validateServiceName(span))
      .flatMap(span => validateOperationName(span))
      .flatMap(span => validateStartTime(span))
      .flatMap(span => validateDuration(span))
    match {
      case Success(span) =>
          val kvPair = extractorConfiguration.outputFormat match {
            case Format.JSON => KeyValuePair(span.getTraceId.getBytes, printer.print(span).getBytes(Charset.forName("UTF-8")))
            case Format.PROTO => KeyValuePair(span.getTraceId.getBytes, recordBytes)
          }
        List(kvPair)

      case Failure(ex) =>
        LOGGER.error("Fail to deserialize the span proto bytes with exception", ex)
        Nil
    }
  }
}
