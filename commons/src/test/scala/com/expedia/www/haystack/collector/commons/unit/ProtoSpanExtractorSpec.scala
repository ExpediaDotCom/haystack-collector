package com.expedia.www.haystack.collector.commons.unit

import com.expedia.open.tracing.Span
import com.expedia.www.haystack.collector.commons.config.{ExtractorConfiguration, Format}
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.SmallestAllowedStartTimeMicros
import org.scalatest.{FunSpec, Matchers}

class ProtoSpanExtractorSpec extends FunSpec with Matchers {

  private val EmptyString = ""
  private val NullString = null
  private val SpanId = "span ID"
  private val TraceId = "trace ID"
  private val ServiceName = "service name"
  private val OperationName = "operation name"
  private val StartTime = System.currentTimeMillis() * 1000
  private val Duration = 42
  private val Negative = -1

  describe("Protobuf Span Extractor") {
    val largestInvalidStartTime = SmallestAllowedStartTimeMicros - 1
    val spanMap = Map(
      "NullSpanId" -> createSpan(NullString, TraceId, ServiceName, OperationName, StartTime, Duration),
      "EmptySpanId" -> createSpan(EmptyString, TraceId, ServiceName, OperationName, StartTime, Duration),
      "NullTraceId" -> createSpan(SpanId, NullString, ServiceName, OperationName, StartTime, Duration),
      "EmptyTraceId" -> createSpan(SpanId, EmptyString, ServiceName, OperationName, StartTime, Duration),
      "NullServiceName" -> createSpan(SpanId, TraceId, NullString, OperationName, StartTime, Duration),
      "EmptyServiceName" -> createSpan(SpanId, TraceId, EmptyString, OperationName, StartTime, Duration),
      "NullOperationName" -> createSpan(SpanId, TraceId, ServiceName, NullString, StartTime, Duration),
      "EmptyOperationName" -> createSpan(SpanId, TraceId, ServiceName, EmptyString, StartTime, Duration),
      "TooSmallStartTime" -> createSpan(SpanId, TraceId, ServiceName, OperationName, largestInvalidStartTime, Duration),
      "NegativeStartTime" -> createSpan(SpanId, TraceId, ServiceName, OperationName, Negative, Duration),
      "TooSmallDuration" -> createSpan(SpanId, TraceId, ServiceName, OperationName, StartTime, Negative)
    )

    it("should fail validation for spans with invalid data") {
      spanMap.foreach(sp => {
        val span = createSpan(sp._2.getSpanId, sp._2.getTraceId, sp._2.getServiceName, sp._2.getOperationName,
          sp._2.getStartTime, sp._2.getDuration)
        val kvPairs = new ProtoSpanExtractor(ExtractorConfiguration(Format.JSON)).extractKeyValuePairs(span.toByteArray)
        withClue(sp._1) {
          kvPairs shouldBe Nil
        }
      })
    }

    val protoSpanExtractor = new ProtoSpanExtractor(ExtractorConfiguration(Format.PROTO))

    it("should pass validation if the number of operation names is below the limit") {
      for (i <- 0 to ProtoSpanExtractor.MaximumOperationNameCount) {
        val span = createSpan(SpanId + i, TraceId + i, ServiceName, OperationName + i, StartTime + i, Duration + i)
        val kvPairs = protoSpanExtractor.extractKeyValuePairs(span.toByteArray)
        kvPairs.size shouldBe 1
      }
    }

    it("should fail validation if the number of operation names is above the limit") {
      val span = createSpan(SpanId, TraceId, ServiceName, OperationName, StartTime, Duration)
      val kvPairs = protoSpanExtractor.extractKeyValuePairs(span.toByteArray)
      kvPairs shouldBe Nil
    }

    it("should clear the set of operation names when the TTL has been reached") {
      val ttlAndOperationNames = ProtoSpanExtractor.ServiceNameVsTtlAndOperationNames.get(ServiceName)
      ttlAndOperationNames.operationNames.size() shouldBe ProtoSpanExtractor.MaximumOperationNameCount + 1
      val span = createSpan(SpanId, TraceId, ServiceName, OperationName, StartTime, Duration)
      protoSpanExtractor.validateOperationNameCount(span, ttlAndOperationNames.getTtlMillis, Duration)
      ttlAndOperationNames.operationNames.size shouldBe 0
    }
  }

  private def createSpan(spanId: String,
                         traceId: String,
                         serviceName: String,
                         operationName: String,
                         startTimeMicros: Long,
                         durationMicros: Long) = {
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
    builder.setStartTime(startTimeMicros)
    builder.setDuration(durationMicros)
    builder.build()
  }
}
