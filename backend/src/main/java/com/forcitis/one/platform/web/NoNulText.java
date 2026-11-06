package com.forcitis.one.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * A string in a request body never holds U+0000: PostgreSQL cannot store it, so it would reach the database and answer 500.
 * It is refused here, once for every module, with the same 400 as any unreadable body; the value is neither stripped nor echoed.
 */
@Configuration
class NoNulText {

    @Bean
    SimpleModule noNulTextModule() {
        return new SimpleModule("no-nul-text").addDeserializer(String.class, new StringDeserializer() {
            @Override
            public String deserialize(JsonParser parser, DeserializationContext context) {
                String text = super.deserialize(parser, context);
                if (text != null && text.indexOf('\u0000') >= 0) {
                    throw context.weirdStringException("<redacted>", String.class, "a text may not contain the NUL character");
                }
                return text;
            }
        });
    }
}
