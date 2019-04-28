package com.expedia.www.haystack.span.decorators.loader;

import com.expedia.www.haystack.span.decorators.SpanDecorator;
import com.typesafe.config.Config;
import org.slf4j.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;

public class SpanDecoratorPluginLoader {
    private Logger logger;
    private PluginConfiguration pluginConfig;
    private static SpanDecoratorPluginLoader spanDecoratorPluginLoader;
    private final ServiceLoader<SpanDecorator> loader;

    private SpanDecoratorPluginLoader() {
        URLClassLoader urlClassLoader = null;
        try {
            urlClassLoader = new URLClassLoader(new URL[] {new File(pluginConfig.directory() + "/" + pluginConfig.jarName()).toURI().toURL()});
        } catch (MalformedURLException ex) {
            logger.error("Could not create the class loader for finding jar {}", ex);
        }
        loader = ServiceLoader.load(SpanDecorator.class, urlClassLoader);
    }

    private SpanDecoratorPluginLoader(Logger logger, PluginConfiguration pluginConfig) {
        this();
        this.logger = logger;
        this.pluginConfig = pluginConfig;
    }

    public static synchronized SpanDecoratorPluginLoader getInstance(Logger logger, PluginConfiguration pluginConfig) {
        if (spanDecoratorPluginLoader == null) {
            spanDecoratorPluginLoader = new SpanDecoratorPluginLoader(logger, pluginConfig);
        }

        return spanDecoratorPluginLoader;
    }

    public SpanDecorator getSpanDecorator() {
        SpanDecorator spanDecorator = null;
        try {
            if (loader.iterator().hasNext()) {
                spanDecorator = loader.iterator().next();
                spanDecorator.init(pluginConfig.config());
            }
        } catch (Exception ex) {
            logger.error("Unable to load the external span decorators {}", ex);
        }

        return spanDecorator;
    }



}
