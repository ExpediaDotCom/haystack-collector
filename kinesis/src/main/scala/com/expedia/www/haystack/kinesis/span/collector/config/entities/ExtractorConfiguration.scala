package com.expedia.www.haystack.kinesis.span.collector.config.entities

import com.expedia.www.haystack.kinesis.span.collector.config.entities.Format.Format


object Format extends Enumeration {
  type Format = Value
  val JSON = Value("json")
  val PROTO = Value("proto")
}

case class ExtractorConfiguration(outputFormat:Format)
