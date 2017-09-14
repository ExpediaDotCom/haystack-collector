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

package com.expedia.www.haystack.kinesis.span.collector.integration.tests

import com.expedia.open.tracing.{Batch, Span}
import com.expedia.www.haystack.kinesis.span.collector.integration._

import scala.concurrent.duration._

class KinesisSpanCollectorSpec extends IntegrationTestSpec {

  "Kinesis span collector" should {

    // this test is primarily to work around issue with Kafka docker image
    // it fails for first put for some reasons
    "connect with kinesis and kafka" in {

      Given("a valid span")
      val span = Span.newBuilder().setTraceId("traceid").setSpanId("span-id-1").build()
      val spanBatchBytes = Batch.newBuilder().addSpans(span).build().toByteArray

      When("the span is sent to kinesis")
      produceRecordsToKinesis(List(spanBatchBytes, spanBatchBytes))

      Then("it should be pushed to kafka")
      readRecordsFromKafka(0, 1.second).headOption
    }

    "read valid spans batch from kinesis and store individual spans in kafka" in {

      Given("valid span batch")
      val span_1 = Span.newBuilder().setTraceId("trace-id-1").setSpanId("span-id-1").build()
      val span_2 = Span.newBuilder().setTraceId("trace-id-1").setSpanId("span-id-2").build()
      val spanBatch_1 = Batch.newBuilder().addSpans(span_1).addSpans(span_2).build().toByteArray

      val span_3 = Span.newBuilder().setTraceId("trace-id-2").setSpanId("span-id-3").build()
      val span_4 = Span.newBuilder().setTraceId("trace-id-2").setSpanId("span-id-4").build()
      val spanBatch_2 = Batch.newBuilder().addSpans(span_3).addSpans(span_4).build().toByteArray

      When("the span batch is sent to kinesis")
      produceRecordsToKinesis(List(spanBatch_1, spanBatch_2))

      Then("it should be pushed to kafka with partition key as its trace id")
      val records = readRecordsFromKafka(4, 5.seconds)
      records should not be empty
      val spans = records.map(Span.parseFrom)
      spans.map(_.getTraceId).toSet should contain allOf("trace-id-1", "trace-id-2")
      spans.map(_.getSpanId) should contain allOf("span-id-1", "span-id-2", "span-id-3", "span-id-4")
    }
  }
}
