package com.expedia.www.haystack.kinesis.span.collector.kinesis.record

import java.nio.ByteBuffer

import com.amazonaws.services.kinesis.model.Record
import com.expedia.open.tracing.Span
import com.expedia.www.haystack.kinesis.span.collector.config.entities.ExtractorConfiguration
import com.expedia.www.haystack.kinesis.span.collector.config.entities.Format
import org.scalatest.FunSpec
import org.scalatest.Matchers

class ProtoSpanExtractorSpec extends FunSpec with Matchers {

  private val EmptyString = ""
  private val NullString = null
  private val SpanId = "span ID"
  private val TraceId = "trace ID"
  private val ServiceName = "service name"
  private val OperationName = "operation name"
  private val StartTimeMicros = System.currentTimeMillis() * 1000
  private val DurationMicros = 42
  private val Zero = 0
  private val Negative = -1

  describe("Protobuf Span Extractor") {
    val spanMap = Map(
      "spanWithNullSpanId" -> createSpan(NullString, TraceId, ServiceName, OperationName, StartTimeMicros, DurationMicros),
      "spanWithEmptySpanId" -> createSpan(EmptyString, TraceId, ServiceName, OperationName, StartTimeMicros, DurationMicros),
      "spanWithNullTraceId" -> createSpan(SpanId, NullString, ServiceName, OperationName, StartTimeMicros, DurationMicros),
      "spanWithEmptyTraceId" -> createSpan(SpanId, EmptyString, ServiceName, OperationName, StartTimeMicros, DurationMicros),
      "spanWithNullServiceName" -> createSpan(SpanId, TraceId, NullString, OperationName, StartTimeMicros, DurationMicros),
      "spanWithEmptyServiceName" -> createSpan(SpanId, TraceId, EmptyString, OperationName, StartTimeMicros, DurationMicros),
      "spanWithNullOperationName" -> createSpan(SpanId, TraceId, ServiceName, NullString, StartTimeMicros, DurationMicros),
      "spanWithEmptyOperationName" -> createSpan(SpanId, TraceId, ServiceName, EmptyString, StartTimeMicros, DurationMicros),
      "spanWithZeroStartTime" -> createSpan(SpanId, TraceId, ServiceName, NullString, Zero, DurationMicros),
      "spanWithNegativeStartTime" -> createSpan(SpanId, TraceId, ServiceName, NullString, Negative, DurationMicros),
      "spanWithZeroDuration" -> createSpan(SpanId, TraceId, ServiceName, EmptyString, StartTimeMicros, Zero),
      "spanWithNegativeDuration" -> createSpan(SpanId, TraceId, ServiceName, EmptyString, StartTimeMicros, Negative)
    )

    spanMap.foreach(sp => {
      val span = createSpan(sp._2.getSpanId, sp._2.getTraceId, sp._2.getServiceName, sp._2.getOperationName,
        sp._2.getStartTime, sp._2.getDuration)
      val kinesisRecord = new Record().withData(ByteBuffer.wrap(span.toByteArray))
      val kvPairs = new ProtoSpanExtractor(ExtractorConfiguration(Format.JSON)).extractKeyValuePairs(kinesisRecord)
      kvPairs shouldBe Nil
    })
  }

  private def createSpan(spanId: String,
                         traceId: String,
                         serviceName: String,
                         operationName: String,
                         startTime: Long,
                         duration: Long) = {
    val builder = Span.newBuilder()
    if (spanId != null) {
      builder.setSpanId(spanId)
    }
    if (traceId != null) {
      builder.setTraceId(traceId)
    }
    if (serviceName != null) {
      builder.setServiceName(serviceName)
    }
    if (operationName != null) {
      builder.setOperationName(operationName)
    }
    if(startTime > 0) {
      builder.setStartTime(startTime)
    }
    if(duration > 0) {
      builder.setDuration(duration)
    }
    builder.build()
  }
}
