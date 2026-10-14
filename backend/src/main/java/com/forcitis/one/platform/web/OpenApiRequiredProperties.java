package com.forcitis.one.platform.web;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every component of a response record is required unless declared @Nullable or Optional. Only records in a web package that a
 * controller really returns are touched; two of them sharing a simple name stop the application, because schemas are keyed by it.
 */
@Configuration
class OpenApiRequiredProperties implements SmartInitializingSingleton {

    private final ApplicationContext context;
    private volatile Map<String, Class<?>> responseRecords = Map.of();

    @Autowired
    OpenApiRequiredProperties(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<ResolvableType> returned = new ArrayList<>();
        for (RequestMappingHandlerMapping mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (HandlerMethod handler : mapping.getHandlerMethods().values()) {
                returned.add(ResolvableType.forMethodReturnType(handler.getMethod(), handler.getBeanType()));
            }
        }
        responseRecords = responseRecords(returned);
    }

    @Bean
    OpenApiCustomizer responsePropertiesAreRequired() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                Class<?> type = responseRecords.get(name);
                if (type == null || schema.getProperties() == null) {
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

    static Map<String, Class<?>> responseRecords(Collection<ResolvableType> returnTypes) {
        Map<String, Class<?>> found = new LinkedHashMap<>();
        Set<Class<?>> visited = new HashSet<>();
        returnTypes.forEach(type -> collect(type, found, visited));
        return found;
    }

    private static void collect(ResolvableType type, Map<String, Class<?>> found, Set<Class<?>> visited) {
        Class<?> raw = type.resolve();
        if (raw != null && raw.isRecord() && isWebType(raw) && visited.add(raw)) {
            Class<?> other = found.putIfAbsent(raw.getSimpleName(), raw);
            if (other != null) {
                throw new IllegalStateException("Two response records share the simple name " + raw.getSimpleName() + ": "
                        + other.getName() + " and " + raw.getName() + " (OpenAPI schemas are keyed by it)");
            }
            for (RecordComponent component : raw.getRecordComponents()) {
                collect(ResolvableType.forType(component.getGenericType(), type), found, visited);
            }
        }
        for (ResolvableType generic : type.getGenerics()) {
            collect(generic, found, visited);
        }
        if (type.isArray()) {
            collect(type.getComponentType(), found, visited);
        }
    }

    private static boolean isWebType(Class<?> type) {
        String name = type.getPackageName();
        return name.endsWith(".web") || name.contains(".web.");
    }

    private static boolean nullable(RecordComponent component) {
        return component.getAnnotatedType().isAnnotationPresent(Nullable.class) || component.getType() == Optional.class;
    }
}
