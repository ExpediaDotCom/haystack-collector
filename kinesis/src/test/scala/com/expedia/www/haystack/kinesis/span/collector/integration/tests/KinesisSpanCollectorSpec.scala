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

import com.expedia.www.haystack.kinesis.span.collector.integration._

class KinesisSpanCollectorSpec extends IntegrationTestSpec {

  "Kinesis span collector" should {

    // this test is primarily to work around issue with Kafka docker image
    // it fails for first put for some reasons
    "connect with kinesis and kafka" in {

      Given("a valid span")
      val span = null

      When("the span is sent to kinesis")
      produceRecordsToKinesis(List(span, span))

      Then("it should be pushed to kafka")
      readRecordsFromKafka.headOption
    }

    "read valid messages from kinesis and store in kafka" in {

      Given("a valid span")
      val span = null

      When("the span is sent to kinesis")
      produceRecordsToKinesis(List(span, span))

      Then("it should be pushed to kafka")
      val record = readRecordsFromKafka.headOption
      record should not be empty
      record.get shouldEqual span
    }

    "should not validate span messages" in {

      Given("an invalid span")
      val invalidSpan = null

      When("the span is sent to kinesis")
      produceRecordsToKinesis(List(invalidSpan))

      Then("it should be pushed to kafka")
      val record = readRecordsFromKafka.headOption
      record should not be empty
      record.get shouldEqual invalidSpan
    }
  }

}
