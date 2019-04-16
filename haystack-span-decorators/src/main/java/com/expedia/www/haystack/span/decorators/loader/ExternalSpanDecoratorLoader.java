package com.expedia.www.haystack.span.decorators.loader;

import com.expedia.www.haystack.span.decorators.SpanDecorator;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.ServiceLoader;

public class ExternalSpanDecoratorLoader {
    private Logger logger;
    private static ExternalSpanDecoratorLoader externalSpanDecoratorLoader;
    private final ServiceLoader<SpanDecorator> loader;

    private ExternalSpanDecoratorLoader() {
        loader = ServiceLoader.load(SpanDecorator.class);
    }

    private ExternalSpanDecoratorLoader(Logger logger) {
        this();
        this.logger = logger;
    }

    public static synchronized ExternalSpanDecoratorLoader getInstance(Logger logger) {
        if (externalSpanDecoratorLoader == null) {
            externalSpanDecoratorLoader = new ExternalSpanDecoratorLoader(logger);
        }

        return externalSpanDecoratorLoader;
    }

    public SpanDecorator getSpanDecorator() {
        SpanDecorator spanDecorator = null;
        try {
            final Iterator<SpanDecorator> spanDecoratorIterator = loader.iterator();
            if (!spanDecoratorIterator.hasNext()) {
                logger.error("No external span com.expedia.www.haystack.span.decorators found");
            } else {
                spanDecorator = spanDecoratorIterator.next();
            }
        } catch (Exception ex) {
            logger.error("Unable to load the external span com.expedia.www.haystack.span.decorators {}", ex);
        }

        return spanDecorator;
    }
}
