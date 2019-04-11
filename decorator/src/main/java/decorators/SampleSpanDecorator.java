package decorators;

import com.expedia.open.tracing.Span;


public class SampleSpanDecorator implements SpanDecorator {

    @Override
    public Span decorate(Span span) {
        return span;
    }
}
