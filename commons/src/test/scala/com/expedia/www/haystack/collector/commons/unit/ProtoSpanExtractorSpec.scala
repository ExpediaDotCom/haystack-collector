package com.expedia.www.haystack.collector.commons.unit

import com.codahale.metrics.Meter
import com.expedia.open.tracing.Span
import com.expedia.www.haystack.collector.commons.config.{ExtractorConfiguration, Format}
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.DurationIsInvalid
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.OperationNameIsRequired
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.ServiceNameIsRequired
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.SmallestAllowedStartTimeMicros
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.SpanIdIsRequired
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.StartTimeIsInvalid
import com.expedia.www.haystack.collector.commons.ProtoSpanExtractor.TraceIdIsRequired
import org.mockito.Mockito
import org.scalatest.{FunSpec, Matchers}
import org.slf4j.Logger
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.verify

import scala.collection.immutable.ListMap

class ProtoSpanExtractorSpec extends FunSpec with Matchers with MockitoSugar {

  private val EmptyString = ""
  private val NullString = null
  private val SpanId = "span ID"
  private val TraceId = "trace ID"
  private val ServiceName1 = "service name 1"
  private val ServiceName2 = "service name 2"
  private val OperationName1 = "operation name 1"
  private val OperationName2 = "operation name 2"
  private val StartTime = System.currentTimeMillis() * 1000
  private val Duration = 42
  private val Negative = -42

  describe("Protobuf Span Extractor") {
    val mockLogger = mock[Logger]
    val mockMeter = mock[Meter]
    val protoSpanExtractor = new ProtoSpanExtractor(ExtractorConfiguration(Format.PROTO), mockMeter, mockLogger)

    val largestInvalidStartTime = SmallestAllowedStartTimeMicros - 1
    // @formatter:off
    val nullSpanIdSpan         = createSpan(NullString,  TraceId,     ServiceName1, OperationName1, StartTime,               Duration)
    val emptySpanIdSpan        = createSpan(EmptyString, TraceId,     ServiceName2, OperationName1, StartTime,               Duration)
    val nullTraceIdSpan        = createSpan(SpanId,      NullString,  ServiceName1, OperationName1, StartTime,               Duration)
    val emptyTraceIdSpan       = createSpan(SpanId,      EmptyString, ServiceName2, OperationName1, StartTime,               Duration)
    val nullServiceNameSpan    = createSpan(SpanId,      TraceId,     NullString,   OperationName1, StartTime,               Duration)
    val emptyServiceNameSpan   = createSpan(SpanId,      TraceId,     EmptyString,  OperationName2, StartTime,               Duration)
    val nullOperationNameSpan  = createSpan(SpanId,      TraceId,     ServiceName1, NullString,     StartTime,               Duration)
    val emptyOperationNameSpan = createSpan(SpanId,      TraceId,     ServiceName2, EmptyString,    StartTime,               Duration)
    val tooSmallStartTimeSpan  = createSpan(SpanId,      TraceId,     ServiceName1, OperationName1, largestInvalidStartTime, Duration)
    val negativeStartTimeSpan  = createSpan(SpanId,      TraceId,     ServiceName2, OperationName1, Negative,                Duration)
    val tooSmallDurationSpan   = createSpan(SpanId,      TraceId,     ServiceName1, OperationName1, StartTime,               Negative)
    val spanMap = ListMap(
      "NullSpanId"         -> (nullSpanIdSpan,         SpanIdIsRequired.format(ServiceName1, OperationName1)),
      "EmptySpanId"        -> (emptySpanIdSpan,        SpanIdIsRequired.format(ServiceName2, OperationName1)),
      "NullTraceId"        -> (nullTraceIdSpan,        TraceIdIsRequired.format(ServiceName1, OperationName1)),
      "EmptyTraceId"       -> (emptyTraceIdSpan,       TraceIdIsRequired.format(ServiceName2, OperationName1)),
      "NullServiceName"    -> (nullServiceNameSpan,    ServiceNameIsRequired.format(nullServiceNameSpan.toString)),
      "EmptyServiceName"   -> (emptyServiceNameSpan,   ServiceNameIsRequired.format(emptyServiceNameSpan.toString)),
      "NullOperationName"  -> (nullOperationNameSpan,  OperationNameIsRequired.format(ServiceName1)),
      "EmptyOperationName" -> (emptyOperationNameSpan, OperationNameIsRequired.format(ServiceName2)),
      "TooSmallStartTime"  -> (tooSmallStartTimeSpan,  StartTimeIsInvalid.format(largestInvalidStartTime, ServiceName1, OperationName1)),
      "NegativeStartTime"  -> (negativeStartTimeSpan,  StartTimeIsInvalid.format(Negative, ServiceName2, OperationName1)),
      "TooSmallDuration"   -> (tooSmallDurationSpan,   DurationIsInvalid.format(Negative, ServiceName1, OperationName1))
    )
    // @formatter:on
    it("should fail validation for spans with invalid data") {
      spanMap.foreach(sp => {
        val kvPairs = protoSpanExtractor.extractKeyValuePairs(sp._2._1.toByteArray)
        withClue(sp._1) {
          kvPairs shouldBe Nil
          verify(mockLogger).error(sp._2._2)
        }
      })
      Mockito.verifyNoMoreInteractions(mockLogger)
    }

    it("should pass validation if the number of operation names is below the limit") {
      for (i <- 0 to ProtoSpanExtractor.MaximumOperationNameCount) {
        val span = createSpan(SpanId + i, TraceId + i, ServiceName1, OperationName1 + i, StartTime + i, Duration + i)
        val kvPairs = protoSpanExtractor.extractKeyValuePairs(span.toByteArray)
        kvPairs.size shouldBe 1
      }
    }

    it("should emit a metric if the number of operation names is above the limit") {
      val span = createSpan(SpanId, TraceId, ServiceName1, OperationName1, StartTime, Duration)
      val kvPairs = protoSpanExtractor.extractKeyValuePairs(span.toByteArray)
      kvPairs.size shouldBe 1
      verify(mockMeter).mark()
      Mockito.verifyNoMoreInteractions(mockMeter)
    }

    it("should clear the set of operation names when the TTL has been reached") {
      val ttlAndOperationNames = ProtoSpanExtractor.ServiceNameVsTtlAndOperationNames.get(ServiceName1)
      ttlAndOperationNames.operationNames.size() shouldBe ProtoSpanExtractor.MaximumOperationNameCount + 1
      val span = createSpan(SpanId, TraceId, ServiceName1, OperationName1, StartTime, Duration)
      protoSpanExtractor.countOperationNamesForService(span, ttlAndOperationNames.getTtlMillis, Duration)
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
