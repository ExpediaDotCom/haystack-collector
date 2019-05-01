package com.expedia.www.haystack.span.decorators;

import com.expedia.open.tracing.Span;
import com.typesafe.config.Config;

public interface SpanDecorator {
    public void init(Config config);
    Span decorate(Span span);
    String name();
}
