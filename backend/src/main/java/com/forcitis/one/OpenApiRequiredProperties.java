package com.forcitis.one;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.classreading.MetadataReader;

/** Response records: every component is required unless declared @Nullable or Optional. Request records keep their own validation. */
@Configuration
class OpenApiRequiredProperties {

    @Bean
    OpenApiCustomizer responsePropertiesAreRequired() {
        Map<String, Class<?>> records = recordsBySimpleName();
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                Class<?> type = records.get(name);
                if (type == null || name.endsWith("Request") || schema.getProperties() == null) {
                    return;
                }
                List<String> required = new ArrayList<>();
                for (RecordComponent component : type.getRecordComponents()) {
                    if (schema.getProperties().containsKey(component.getName()) && !nullable(component)) {
                        required.add(component.getName());
                    }
                }
                required.sort(null);
                schema.setRequired(required.isEmpty() ? null : required);
            });
        };
    }

    private static boolean nullable(RecordComponent component) {
        return component.getAnnotatedType().isAnnotationPresent(Nullable.class) || component.getType() == Optional.class;
    }

    private static Map<String, Class<?>> recordsBySimpleName() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter((MetadataReader reader, org.springframework.core.type.classreading.MetadataReaderFactory factory) ->
                "java.lang.Record".equals(reader.getClassMetadata().getSuperClassName()));
        Map<String, Class<?>> records = new HashMap<>();
        scanner.findCandidateComponents("com.forcitis.one").forEach(definition -> {
            try {
                Class<?> type = Class.forName(definition.getBeanClassName());
                records.put(type.getSimpleName(), type);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        });
        return records;
    }
}
