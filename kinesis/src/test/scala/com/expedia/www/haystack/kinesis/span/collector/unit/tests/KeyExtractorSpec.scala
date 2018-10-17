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

package com.expedia.www.haystack.kinesis.span.collector.unit.tests

import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.amazonaws.services.kinesis.model.Record
import com.expedia.open.tracing.Span
import com.expedia.www.haystack.kinesis.span.collector.config.entities.ExtractorConfiguration
import com.expedia.www.haystack.kinesis.span.collector.config.entities.Format
import com.expedia.www.haystack.kinesis.span.collector.kinesis.record.ProtoSpanExtractor
import com.google.protobuf.util.JsonFormat
import org.scalatest.FunSpec
import org.scalatest.Matchers

class KeyExtractorSpec extends FunSpec with Matchers {
  private val StartTimeMicros = System.currentTimeMillis() * 1000
  private val DurationMicros = 42

  describe("TransactionId Key Extractor with proto output type") {
    it("should read the proto span object and set the right partition key and set value as the proto bytestream") {
      val spanMap = Map(
        "trace-id-1" -> createSpan("trace-id-1", "spanId_1", "service_1", "operation", StartTimeMicros, DurationMicros),
        "trace-id-2" -> createSpan("trace-id-2", "spanId_2", "service_2", "operation", StartTimeMicros, DurationMicros))

      spanMap.foreach(sp => {
        val kinesisRecord = new Record().withData(ByteBuffer.wrap(sp._2.toByteArray))

        val kvPairs = new ProtoSpanExtractor(ExtractorConfiguration(Format.PROTO)).extractKeyValuePairs(kinesisRecord)
        kvPairs.size shouldBe 1

        kvPairs.head.key shouldBe sp._1.getBytes
        kvPairs.head.value shouldBe sp._2.toByteArray
      })
    }
  }

  describe("TransactionId Key Extractor with json output type") {
    it("should read the proto span object and set the right partition key and set value as the json bytestream") {
      val spanMap = Map(
        "trace-id-1" -> createSpan("trace-id-1", "spanId_1", "service_1", "operation", StartTimeMicros, 1),
        "trace-id-2" -> createSpan("trace-id-2", "spanId_2", "service_2", "operation", StartTimeMicros, 1))

      spanMap.foreach(sp => {
        val kinesisRecord = new Record().withData(ByteBuffer.wrap(sp._2.toByteArray))

        val kvPairs = new ProtoSpanExtractor(ExtractorConfiguration(Format.JSON)).extractKeyValuePairs(kinesisRecord)
        kvPairs.size shouldBe 1

        kvPairs.head.key shouldBe sp._1.getBytes
        kvPairs.head.value shouldBe JsonFormat.printer().omittingInsignificantWhitespace().print(sp._2).getBytes(Charset.forName("UTF-8"))
      })
    }
  }

  private def createSpan(traceId: String, spanId: String, serviceName: String, operationName: String,
                         startTime: Long, duration: Long) = {
    Span.newBuilder()
      .setServiceName(serviceName)
      .setTraceId(traceId)
      .setSpanId(spanId)
      .setOperationName(operationName)
      .setStartTime(startTime)
      .setDuration(duration)
      .build()
  }
}
