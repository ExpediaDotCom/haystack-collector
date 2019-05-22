package com.expedia.www.haystack.collector.commons

import com.expedia.www.haystack.span.decorators.plugin.config.Plugin
import com.expedia.www.haystack.span.decorators.{AdditionalTagsSpanDecorator, SpanDecorator}
import com.expedia.www.haystack.span.decorators.plugin.loader.SpanDecoratorPluginLoader
import org.slf4j.Logger

import scala.collection.JavaConverters._

object Utils {
  def getSpanDecoratorList(pluginConfig: Plugin, additionalTagsConfig: Map[String, String], LOGGER: Logger): List[SpanDecorator] = {
    var tempList: List[SpanDecorator] = List()
    if (pluginConfig != null) {
      val externalSpanDecorators: List[SpanDecorator] = SpanDecoratorPluginLoader.getInstance(LOGGER, pluginConfig).getSpanDecorators().asScala.toList
      if (externalSpanDecorators != null) {
        tempList = tempList.++:(externalSpanDecorators)
      }
    }

    val additionalTagsSpanDecorator = new AdditionalTagsSpanDecorator(additionalTagsConfig.asJava, LOGGER)
    tempList.::(additionalTagsSpanDecorator)
  }
}
