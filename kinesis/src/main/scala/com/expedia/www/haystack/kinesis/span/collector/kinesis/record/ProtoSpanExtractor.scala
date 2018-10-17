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

package com.expedia.www.haystack.kinesis.span.collector.kinesis.record

import java.nio.charset.Charset

import com.amazonaws.services.kinesis.model.Record
import com.expedia.open.tracing.Span
import com.expedia.www.haystack.kinesis.span.collector.config.entities.{ExtractorConfiguration, Format}
import com.google.protobuf.util.JsonFormat
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

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
    validate(span, span.getStartTime, "Start time is required: span ID=%s", span.getSpanId)
  }

  def validateDuration(span: Span): Try[Span] = {
    validate(span, span.getDuration, "Duration is required: span ID=%s", span.getSpanId)
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
                       additionalInfoForMsg: String) = {
    if (valueToValidate <= 0) {
      Failure(new IllegalArgumentException(msg.format(additionalInfoForMsg)))
    } else {
      Success(span)
    }
  }

  override def extractKeyValuePairs(record: Record): List[KeyValuePair[Array[Byte], Array[Byte]]] = {
    val recordBytes = record.getData.array()
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
            case Format.PROTO => KeyValuePair(span.getTraceId.getBytes, span.toByteArray)
          }
        List(kvPair)

      case Failure(ex) =>
        LOGGER.error("Fail to deserialize the span proto bytes with exception", ex)
        Nil
    }
  }
}
