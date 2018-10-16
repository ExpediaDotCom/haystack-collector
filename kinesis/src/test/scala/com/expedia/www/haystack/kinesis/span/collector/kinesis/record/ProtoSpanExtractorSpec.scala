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

  describe("Protobuf Span Extractor") {
    val spanMap = Map(
      "spanWithNullSpanId" -> createSpan(NullString, TraceId, ServiceName, OperationName),
      "spanWithEmptySpanId" -> createSpan(EmptyString, TraceId, ServiceName, OperationName),
      "spanWithNullTraceId" -> createSpan(SpanId, NullString, ServiceName, OperationName),
      "spanWithEmptyTraceId" -> createSpan(SpanId, EmptyString, ServiceName, OperationName),
      "spanWithNullServiceName" -> createSpan(SpanId, TraceId, NullString, OperationName),
      "spanWithEmptyServiceName" -> createSpan(SpanId, TraceId, EmptyString, OperationName),
      "spanWithNullOperationName" -> createSpan(SpanId, TraceId, ServiceName, NullString),
      "spanWithEmptyOperationName" -> createSpan(SpanId, TraceId, ServiceName, EmptyString))

    spanMap.foreach(sp => {
      val span = createSpan(sp._2.getSpanId, sp._2.getTraceId, sp._2.getServiceName, sp._2.getOperationName)
      val kinesisRecord = new Record().withData(ByteBuffer.wrap(span.toByteArray))
      val kvPairs = new ProtoSpanExtractor(ExtractorConfiguration(Format.JSON)).extractKeyValuePairs(kinesisRecord)
      kvPairs shouldBe Nil
    })
  }

  private def createSpan(spanId: String,
                         traceId: String,
                         serviceName: String,
                         operationName: String) = {
    val builder = Span.newBuilder()
    if (spanId != null)
      builder.setSpanId(spanId)
    if (traceId != null)
      builder.setTraceId(traceId)
    if (serviceName != null)
      builder.setServiceName(serviceName)
    if (operationName != null)
      builder.setOperationName(operationName)
    builder.build()
  }
}
