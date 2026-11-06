package com.forcitis.one.accounting.period.web;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * The year of an open-fiscal-year request is a JSON integer. The default reader would turn 2026.9, "2027" or true into a year, so this request is read
 * by hand: a year of any other shape is refused with 400 (#1551).
 */
@Configuration
class FiscalYearRequestModule {

    @Bean
    SimpleModule openFiscalYearRequestModule() {
        return new SimpleModule("fiscal-year-request").addDeserializer(OpenFiscalYearRequest.class, new ValueDeserializer<>() {
            @Override
            public OpenFiscalYearRequest deserialize(JsonParser parser, DeserializationContext context) {
                JsonNode body = context.readTree(parser);
                JsonNode entity = body.get("legalEntityId");
                JsonNode year = body.get("fiscalYear");
                UUID legalEntityId = entity == null || entity.isNull() ? null : context.readTreeAsValue(entity, UUID.class);
                Integer fiscalYear = null;
                if (year != null && !year.isNull()) {
                    if (!year.isIntegralNumber() || !year.canConvertToInt()) {
                        return context.reportInputMismatch(OpenFiscalYearRequest.class, "fiscalYear must be a JSON integer");
                    }
                    fiscalYear = year.intValue();
                }
                return new OpenFiscalYearRequest(legalEntityId, fiscalYear);
            }
        });
    }
}
