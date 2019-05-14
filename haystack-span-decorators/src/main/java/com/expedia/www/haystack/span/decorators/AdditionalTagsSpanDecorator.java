package com.expedia.www.haystack.span.decorators;

import com.expedia.open.tracing.Span;
import com.expedia.open.tracing.Tag;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.stream.Collectors;

public class AdditionalTagsSpanDecorator implements SpanDecorator {

    private final Map<String, String> tagConfig;
    private final Logger logger;

    public AdditionalTagsSpanDecorator(Map<String, String> tagConfig, Logger logger) {
        this.tagConfig = tagConfig;
        this.logger = logger;
    }

    @Override
    public void init(Config config) {

    }

    @Override
    public Span decorate(Span span) {
        return addHaystackMetadataTags(span);
    }

    @Override
    public String name() {
        return AdditionalTagsSpanDecorator.class.getName();
    }

    private Span addHaystackMetadataTags(Span span) {
        final Span.Builder spanBuilder = span.toBuilder();

        final Map<String, String> spanTags = span.getTagsList().stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getVStr));

        tagConfig.forEach((k, v) -> {
            final String tagValue = spanTags.getOrDefault(k, null);
            if (StringUtils.isEmpty(tagValue)) {
                spanBuilder.addTags(Tag.newBuilder().setKey(k).setVStr(v).build());
            }
        });

        return spanBuilder.build();
    }

}
