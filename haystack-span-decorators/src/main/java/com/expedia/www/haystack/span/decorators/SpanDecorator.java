package com.expedia.www.haystack.span.decorators;

import com.expedia.open.tracing.Span;

public interface SpanDecorator {
    public Span decorate(Span span);
}
