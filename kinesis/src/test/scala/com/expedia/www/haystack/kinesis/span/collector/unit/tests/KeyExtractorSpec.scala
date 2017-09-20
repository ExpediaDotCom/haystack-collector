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
import com.expedia.open.tracing.{Batch, Span}
import com.expedia.www.haystack.kinesis.span.collector.config.entities.{ExtractorConfiguration, Format}
import com.expedia.www.haystack.kinesis.span.collector.kinesis.record.ProtoSpanExtractor
import com.google.protobuf.util.JsonFormat
import org.scalatest.{FunSpec, Matchers}

class KeyExtractorSpec extends FunSpec with Matchers {

  describe("TransactionId Key Extractor with proto output type") {
    it("should read the proto span object and set the right partition key and set value as the proto bytestream") {
      val spanMap = Map(
        "trace-id-1" -> createSpan("trace-id-1", "spanId_1", "service_1"),
        "trace-id-2" -> createSpan("trace-id-2", "spanId_2", "service_2"))

      val batchBuilder = Batch.newBuilder()
      spanMap.values.foreach(span => batchBuilder.addSpans(span))

      val kinesisRecord = new Record().withData(ByteBuffer.wrap(batchBuilder.build().toByteArray))

      val kvPairs =  new ProtoSpanExtractor(ExtractorConfiguration(Format.PROTO)).extractKeyValuePairs(kinesisRecord)
      kvPairs.size shouldBe spanMap.size

      spanMap.foreach {
        case (traceId, span) =>
          val spanKv = kvPairs.find(kv => new String(kv.key) == traceId).get
          spanKv.key shouldBe traceId.getBytes
          spanKv.value shouldBe span.toByteArray
      }
    }
  }

  describe("TransactionId Key Extractor with json output type") {
    it("should read the proto span object and set the right partition key and set value as the json bytestream") {
      val spanMap = Map(
        "trace-id-1" -> createSpan("trace-id-1", "spanId_1", "service_1"),
        "trace-id-2" -> createSpan("trace-id-2", "spanId_2", "service_2"))

      val batchBuilder = Batch.newBuilder()
      spanMap.values.foreach(span => batchBuilder.addSpans(span))

      val kinesisRecord = new Record().withData(ByteBuffer.wrap(batchBuilder.build().toByteArray))

      val kvPairs =  new ProtoSpanExtractor(ExtractorConfiguration(Format.JSON)).extractKeyValuePairs(kinesisRecord)
      kvPairs.size shouldBe spanMap.size

      spanMap.foreach {
        case (traceId, span) =>
          val spanKv = kvPairs.find(kv => new String(kv.key) == traceId).get
          spanKv.key shouldBe traceId.getBytes
          spanKv.value shouldBe JsonFormat.printer().omittingInsignificantWhitespace().print(span).getBytes(Charset.forName("UTF-8"))
      }
    }
  }

  private def createSpan(traceId: String, spanId: String, serviceName: String) = {
    val process = com.expedia.open.tracing.Process.newBuilder().setServiceName(serviceName).build()
    Span.newBuilder().setProcess(process).setTraceId(traceId).setSpanId(spanId).build()
  }
}
