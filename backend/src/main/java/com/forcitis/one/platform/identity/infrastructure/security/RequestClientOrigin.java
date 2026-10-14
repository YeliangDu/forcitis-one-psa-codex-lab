package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.application.ClientOrigin;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** X-Forwarded-For is honoured only from the trusted proxy (config/platform_identity.properties); a header sent by a client is ignored. */
@Component
class RequestClientOrigin implements ClientOrigin {

    @Override
    public String address() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRemoteAddr();
        }
        return "unknown";
    }

    @Override
    public String userAgent() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getHeader("User-Agent");
        }
        return null;
    }
}
