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
 * Every component of a response record is required unless declared @Nullable (JSpecify or Spring) or Optional. Only records in a web package that a
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
                if (name.startsWith("CursorPage") && schema.getProperties() != null && schema.getProperties().containsKey("nextCursor")) {
                    schema.setRequired(List.of("items"));
                    markNullable((io.swagger.v3.oas.models.media.Schema<?>) schema.getProperties().get("nextCursor"));
                    return;
                }
                Class<?> type = responseRecords.get(name);
                if (type == null || schema.getProperties() == null) {
                    return;
                }
                List<String> required = new ArrayList<>();
                for (RecordComponent component : type.getRecordComponents()) {
                    if (!schema.getProperties().containsKey(component.getName())) {
                        continue;
                    }
                    if (!nullable(component)) {
                        required.add(component.getName());
                    } else {
                        markNullable((io.swagger.v3.oas.models.media.Schema<?>) schema.getProperties().get(component.getName()));
                    }
                }
                required.sort(null);
                schema.setRequired(required.isEmpty() ? null : required);
            });
        };
    }

    /** OpenAPI 3.1: a value that may be null says so in its type list. A reference cannot: it is only left out of "required" (the client reads null as absent). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void markNullable(io.swagger.v3.oas.models.media.Schema property) {
        java.util.Set<String> types = property.getTypes() != null ? new java.util.LinkedHashSet<>(property.getTypes())
                : property.getType() != null ? new java.util.LinkedHashSet<>(java.util.Set.of(property.getType())) : null;
        if (types != null) {
            types.add("null");
            property.setTypes(types);
            property.setType(null);
        }
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
