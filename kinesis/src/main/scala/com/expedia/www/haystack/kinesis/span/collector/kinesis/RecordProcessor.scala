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

package com.expedia.www.haystack.kinesis.span.collector.kinesis

import java.util.Date

import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.ShutdownReason
import com.amazonaws.services.kinesis.clientlibrary.types.{InitializationInput, ProcessRecordsInput, ShutdownInput}
import com.expedia.www.haystack.kinesis.span.collector.config.entities.KinesisConsumerConfiguration
import com.expedia.www.haystack.kinesis.span.collector.kinesis.record.KeyValueExtractor
import com.expedia.www.haystack.kinesis.span.collector.metrics.MetricsSupport
import com.expedia.www.haystack.kinesis.span.collector.sink.RecordSink
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object RecordProcessor extends MetricsSupport {
  private val LOGGER = LoggerFactory.getLogger(classOf[RecordProcessor])
  private val ingestionSuccessMeter = metricRegistry.meter(s"kinesis.ingestion-success")
  private val processingLagHistogram = metricRegistry.histogram("kinesis.processing.lag")
}

class RecordProcessor(config: KinesisConsumerConfiguration, keyValueExtractor: KeyValueExtractor, sink: RecordSink)
  extends IRecordProcessor {

  import RecordProcessor._

  private var shardId: String = _
  private var nextCheckpointTimeInMillis: Long = 0L

  private def checkpoint(checkpointer: IRecordProcessorCheckpointer): Unit = {
    LOGGER.debug(s"Performing the checkpointing for shardId=$shardId")

    retryWithBackOff(config.checkpointRetries, config.checkpointRetryInterval)(() => {
      checkpointer.checkpoint()
    }) match {
      case Failure(r) => LOGGER.error(s"Fail to checkpoint after all retries for shardId=$shardId with reason", r)
      case _ => LOGGER.info(s"Successfully checkpointing done for shardId=$shardId")
    }
  }

  /**
    * process the incoming kinesis records. This processor extracts the traceId (partition key for kafka) and
    * span as byte array.
    * @param records kinesis records
    */
  override def processRecords(records: ProcessRecordsInput): Unit = {
    var lastRecordArrivalTimestamp:Date = null

    records
      .getRecords
      .foreach(record => {
        lastRecordArrivalTimestamp = record.getApproximateArrivalTimestamp
        Try(keyValueExtractor.extractKeyValuePairs(record)) match {
          case Success(spans) => spans.foreach(sp => sink.toAsync(sp, null))
          case _ => /* skip logging as extractor does it*/
        }
      })

    // this is somewhat similar to the IteratorAgeMilliseconds metric reported by Cloudwatch for Kinesis stream
    if(lastRecordArrivalTimestamp != null) {
      processingLagHistogram.update(System.currentTimeMillis() - lastRecordArrivalTimestamp.getTime)
    }

    ingestionSuccessMeter.mark(records.getRecords.size())

    if (System.currentTimeMillis > nextCheckpointTimeInMillis) {
      checkpoint(records.getCheckpointer)
      nextCheckpointTimeInMillis = System.currentTimeMillis + config.checkpointInterval.toMillis
    }
  }

  /**
    * initialize the kinesis record processor
    * @param input: initialization input contains the shardId and sequenceNumber
    */
  override def initialize(input: InitializationInput): Unit = {
    LOGGER.info(s"Initializing the processor for shardId=${input.getShardId} and SeqNumber=${input.getExtendedSequenceNumber}")
    this.shardId = input.getShardId
  }

  /**
    * shutdown the processor, it shutdown reason is terminate, then perform the pending checkpointing.
    * @param shutdownInput: shutdown input that contains the reason
    */
  override def shutdown(shutdownInput: ShutdownInput): Unit = {
    LOGGER.info(s"Shutting down record processor for shardId=$shardId")

    // Important to checkpoint after reaching end of shard, so we can start processing data from child shards.
    if (shutdownInput.getShutdownReason == ShutdownReason.TERMINATE) {
      checkpoint(shutdownInput.getCheckpointer)
    }
  }

  @tailrec
  final def retryWithBackOff[T](maxRetry: Int, backOff: FiniteDuration)(f: () => T): Try[T] = {
    Try {
      f()
    } match {
      case Failure(reason) if maxRetry > 0 && !reason.isInstanceOf[InterruptedException] && !reason.isInstanceOf[ShutdownException] =>
        LOGGER.error(s"Fail to perform the checkpointing operation with retries left=$maxRetry ", reason)
        Thread.sleep(backOff.toMillis)
        retryWithBackOff(maxRetry - 1, backOff)(f)
      case result@_ => result
    }
  }
}
