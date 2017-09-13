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

case class KeyValuePair[K, V](key: K, value: V)

trait KeyValueExtractor {
  def configure(): Unit
  def extractKeyValuePairs(record: Record): List[KeyValuePair[Array[Byte], Array[Byte]]]
}
