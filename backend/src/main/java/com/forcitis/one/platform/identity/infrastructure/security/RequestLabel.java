package com.forcitis.one.platform.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.servlet.HandlerMapping;

/**
 * The request as an audit text: the route template the code declares plus the path variables that are canonical uuids, never the caller's
 * raw path, which may be long or look like a secret. A request refused before routing has no template and reads {@code unmatched}.
 */
final class RequestLabel {

    static final String UNMATCHED = "unmatched";

    private RequestLabel() {
    }

    static String of(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String template) || template.isBlank()) {
            return request.getMethod() + " " + UNMATCHED;
        }
        return request.getMethod() + " " + template + identifiers(request);
    }

    private static String identifiers(HttpServletRequest request) {
        if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables)) {
            return "";
        }
        String text = variables.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof String value && isCanonicalUuid(value))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? "" : " " + text;
    }

    private static boolean isCanonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
