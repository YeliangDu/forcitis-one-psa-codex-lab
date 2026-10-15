package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.ListLimits;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.math.BigDecimal;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Every {@code limit} query parameter declares the one range of the system, so the generated client and the contract agree with the server (A-3). */
@Configuration
class OpenApiListLimit {

    @Bean
    OpenApiCustomizer limitRange() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                if (operation.getParameters() == null) {
                    return;
                }
                for (Parameter parameter : operation.getParameters()) {
                    if ("query".equals(parameter.getIn()) && "limit".equals(parameter.getName())) {
                        IntegerSchema range = new IntegerSchema();
                        range.setFormat("int32");
                        range.setMinimum(BigDecimal.ONE);
                        range.setMaximum(BigDecimal.valueOf(ListLimits.MAX_LIMIT));
                        range.setDefault(ListLimits.DEFAULT_LIMIT);
                        parameter.setSchema(range);
                    }
                }
            }));
        };
    }
}
