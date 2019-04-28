package com.expedia.www.haystack.span.decorators.loader

import com.typesafe.config.Config

case class PluginConfiguration(directory: String, jarName: String, name: String, config: Config)
