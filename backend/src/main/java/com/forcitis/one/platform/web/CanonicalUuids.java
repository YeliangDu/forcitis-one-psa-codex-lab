package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.Uuids;
import java.beans.PropertyEditorSupport;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * A UUID that reaches the API as a path or query parameter or inside a body is read in its one canonical form (lower case, 8-4-4-4-12)
 * and is never the nil UUID, so an id has one spelling and "no key" is not an id. Other forms are refused with 400. A property editor
 * does this for parameters because Spring falls back to its own lenient editor when a converter refuses.
 */
@Configuration
class CanonicalUuids {

    @ControllerAdvice
    static class PathAndQuery {

        @InitBinder
        void canonicalUuids(WebDataBinder binder) {
            binder.registerCustomEditor(UUID.class, new PropertyEditorSupport() {
                @Override
                public void setAsText(String text) {
                    setValue(read(text));
                }
            });
        }
    }

    @Bean
    SimpleModule canonicalUuidModule() {
        return new SimpleModule("canonical-uuid").addDeserializer(UUID.class, new ValueDeserializer<>() {
            @Override
            public UUID deserialize(JsonParser parser, DeserializationContext context) {
                String text = parser.getValueAsString();
                try {
                    return read(text);
                } catch (IllegalArgumentException refused) {
                    throw context.weirdStringException(text, UUID.class, refused.getMessage());
                }
            }
        });
    }

    static UUID read(String text) {
        UUID id = Uuids.parseCanonical(text);
        if (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0) {
            throw new IllegalArgumentException("the nil UUID is no id");
        }
        return id;
    }
}
