package com.forcitis.one.platform.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/** The request as an audit text: the route template the code declares, never the caller's raw path, which may be long or look like a secret. */
final class RequestLabel {

    static final String UNMATCHED = "unmatched";

    private RequestLabel() {
    }

    static String of(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return request.getMethod() + " " + (pattern instanceof String template && !template.isBlank() ? template : UNMATCHED);
    }
}
