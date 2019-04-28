package com.expedia.www.haystack.span.decorators;

import com.expedia.open.tracing.Span;
import com.typesafe.config.Config;


public class SampleSpanDecorator implements SpanDecorator {

    @Override
    public void init(Config config) {

    }

    @Override
    public Span decorate(Span span) {
        return span;
    }
}
