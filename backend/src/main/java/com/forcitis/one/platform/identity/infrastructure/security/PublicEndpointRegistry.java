package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.PublicEndpoint;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** The set must equal architecture/public-endpoints.txt (checked by ApplicationIntegrationTest, I-3). */
@Component
class PublicEndpointRegistry {

    record Entry(HttpMethod method, String pattern, String reason) {
    }

    private final List<Entry> entries = new ArrayList<>();

    PublicEndpointRegistry(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping) {
        for (var mapped : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = mapped.getKey();
            HandlerMethod handler = mapped.getValue();
            PublicEndpoint marker = handler.getMethodAnnotation(PublicEndpoint.class);
            if (marker == null) {
                continue;
            }
            if (info.getMethodsCondition().getMethods().isEmpty()) {
                throw new IllegalStateException("@PublicEndpoint handler " + handler + " must declare its HTTP method");
            }
            for (var method : info.getMethodsCondition().getMethods()) {
                for (String pattern : info.getPatternValues()) {
                    entries.add(new Entry(HttpMethod.valueOf(method.name()), pattern, marker.reason()));
                }
            }
        }
    }

    List<Entry> entries() {
        return List.copyOf(entries);
    }
}
