package com.expedia.www.haystack.kinesis.span.collector.kinesis.record

import java.nio.ByteBuffer

import com.amazonaws.services.kinesis.model.Record
import com.expedia.open.tracing.Span
import com.expedia.www.haystack.kinesis.span.collector.config.entities.ExtractorConfiguration
import com.expedia.www.haystack.kinesis.span.collector.config.entities.Format
import org.scalatest.FunSpec
import org.scalatest.Matchers

class ProtoSpanExtractorSpec extends FunSpec with Matchers {
  describe("Protobuf Span Extractor") {
    it("should return Nil if no operation name is specified") {
      val span = Span.newBuilder().build()
      val kinesisRecord = new Record().withData(ByteBuffer.wrap(span.toByteArray))
      val kvPairs = new ProtoSpanExtractor(ExtractorConfiguration(Format.JSON)).extractKeyValuePairs(kinesisRecord)
      kvPairs shouldBe Nil
    }
    it("should return Nil if an empty operation name is specified") {
      val span = Span.newBuilder().setOperationName("").build()
      val kinesisRecord = new Record().withData(ByteBuffer.wrap(span.toByteArray))
      val kvPairs = new ProtoSpanExtractor(ExtractorConfiguration(Format.JSON)).extractKeyValuePairs(kinesisRecord)
      kvPairs shouldBe Nil
    }
  }

}
