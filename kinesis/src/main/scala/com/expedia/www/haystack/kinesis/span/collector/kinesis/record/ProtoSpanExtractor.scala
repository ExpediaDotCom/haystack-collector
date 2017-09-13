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

import com.amazonaws.services.kinesis.model.Record
import com.expedia.open.tracing.Batch
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

class ProtoSpanExtractor extends KeyValueExtractor {
  private val LOGGER = LoggerFactory.getLogger(classOf[ProtoSpanExtractor])

  override def configure(): Unit = ()

  override def extractKeyValuePairs(record: Record): List[KeyValuePair[Array[Byte], Array[Byte]]] = {
    val recordBytes = record.getData.array()
    Try(Batch.parseFrom(recordBytes)) match {
      case Success(batch) =>
        val spans = for(span <- batch.getSpansList) yield KeyValuePair(span.getTraceId.getBytes, span.toByteArray)
        spans.toList
      case Failure(ex) =>
        LOGGER.error("Fail to deserialize the span proto bytes with exception", ex)
        Nil
    }
  }
}
