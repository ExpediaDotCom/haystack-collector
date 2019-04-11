package decorators;

import com.expedia.open.tracing.Span;
import com.expedia.open.tracing.Tag;

import java.util.Map;

public class AdditionalTagsSpanDecorator implements SpanDecorator {

    private final Map<String, String> tagConfig;

    public AdditionalTagsSpanDecorator(Map<String, String> tagConfig) {
        this.tagConfig = tagConfig;
    }

    @Override
    public Span decorate(Span span) {
        return addHaystackMetadataTags(span);
    }

    private Span addHaystackMetadataTags(Span span) {
        final Span.Builder spanBuilder = span.toBuilder();
        tagConfig.entrySet().stream().map(t -> {
            return Tag.newBuilder().setKey(t.getKey()).setVStr(t.getValue()).build();
        }).forEach(t -> spanBuilder.addTags(t));

        return spanBuilder.build();
    }
}
