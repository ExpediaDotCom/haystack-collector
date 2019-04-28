package com.expedia.www.haystack.span.decorators;

import com.expedia.open.tracing.Span;
import com.expedia.open.tracing.Tag;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Map;

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

    private Span addHaystackMetadataTags(Span span) {
        final Span.Builder spanBuilder = span.toBuilder();
        span.getTagsList().stream().forEach(tag -> {
            try {
                final String tagValue = tagConfig.get(tag.getKey());
                if (tagValue != null && StringUtils.isEmpty(tag.getVStr())) {
                    spanBuilder.addTags(Tag.newBuilder().setKey(tag.getKey())
                            .setVStr(tagValue).build());
                }
            } catch (Exception ex) {
                logger.debug("No matching tag found in tagConfig for this tag");
            }

        });

        return spanBuilder.build();
    }

}
