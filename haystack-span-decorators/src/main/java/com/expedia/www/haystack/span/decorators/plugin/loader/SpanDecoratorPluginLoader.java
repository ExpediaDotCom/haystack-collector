package com.expedia.www.haystack.span.decorators.plugin.loader;

import com.expedia.www.haystack.span.decorators.SpanDecorator;

import com.expedia.www.haystack.span.decorators.plugin.config.Plugin;
import com.expedia.www.haystack.span.decorators.plugin.config.PluginConfiguration;
import org.slf4j.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class SpanDecoratorPluginLoader {
    private Logger logger;
    private Plugin pluginConfig;
    private static SpanDecoratorPluginLoader spanDecoratorPluginLoader;
    private final ServiceLoader<SpanDecorator> loader;

    private SpanDecoratorPluginLoader() {
        URLClassLoader urlClassLoader = null;
        try {
            urlClassLoader = new URLClassLoader(new URL[] {new File(pluginConfig.getDirectory() + "/").toURI().toURL()});
        } catch (MalformedURLException ex) {
            logger.error("Could not create the class loader for finding jar ", ex);
        }
        loader = ServiceLoader.load(SpanDecorator.class, urlClassLoader);
    }

    private SpanDecoratorPluginLoader(Logger logger, Plugin pluginConfig) {
        this();
        this.logger = logger;
        this.pluginConfig = pluginConfig;
    }

    public static synchronized SpanDecoratorPluginLoader getInstance(Logger logger, Plugin pluginConfig) {
        if (spanDecoratorPluginLoader == null) {
            spanDecoratorPluginLoader = new SpanDecoratorPluginLoader(logger, pluginConfig);
        }

        return spanDecoratorPluginLoader;
    }

    public List<SpanDecorator> getSpanDecorators() {
        List<SpanDecorator> spanDecorators = new ArrayList<>();
        try {
            loader.forEach((spanDecorator) -> {
                final PluginConfiguration validFirstConfig = pluginConfig.getPluginConfigurationList().stream().filter(pluginConfiguration ->
                        pluginConfiguration.getName().equals(spanDecorator.name())).findFirst().orElse(null);
                if (validFirstConfig != null) {
                    spanDecorator.init(validFirstConfig.getConfig());
                    spanDecorators.add(spanDecorator);
                    logger.info("Successfully loaded the plugin ", spanDecorator.name());
                }
            });
        } catch (Exception ex) {
            logger.error("Unable to load the external span decorators ", ex);
        }

        return spanDecorators;
    }



}
