package com.forcitis.one;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The URL carries "v1" while the mappings declare version "1"; Spring's own path resolver would also accept /api/1/. */
@Configuration
class ApiVersioning implements WebMvcConfigurer {

    private static final String VERSION = "1";
    private static final Pattern SEGMENT = Pattern.compile("^/api/v(\\d+)(?:/|$)");
    private static final Pattern TEMPLATE = Pattern.compile("^/api/\\{version}/");

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useVersionResolver(request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            Matcher matcher = SEGMENT.matcher(path);
            return matcher.find() ? matcher.group(1) : null;
        }).addSupportedVersions(VERSION);
    }

    @Bean
    OpenApiCustomizer versionedPaths() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            Paths renamed = new Paths();
            for (var entry : openApi.getPaths().entrySet()) {
                PathItem item = entry.getValue();
                renamed.addPathItem(TEMPLATE.matcher(entry.getKey()).replaceFirst("/api/v" + VERSION + "/"), item);
            }
            openApi.setPaths(renamed);
        };
    }
}
