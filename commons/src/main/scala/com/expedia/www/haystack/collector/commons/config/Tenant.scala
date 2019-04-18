package com.expedia.www.haystack.collector.commons.config

case class Tenant(id:Int,
                  name: String,
                  isShared: Boolean,
                  ingestionTypes: List[String]= List("Haystack"),
                  tags: Map[String, String])

class IngestionType extends Enumeration {
  type IngestionType = Value
  val HAYSTACK, ZIPKIN = Value
}