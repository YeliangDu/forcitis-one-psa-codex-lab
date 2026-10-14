package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.application.ClientOrigin;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The caller's address as the servlet container resolved it. With server.forward-headers-strategy=native and
 * server.tomcat.remoteip.internal-proxies limited to nginx (config/platform_identity.properties), X-Forwarded-For is honoured
 * only when it comes from the trusted proxy; a header sent by a client directly or through an untrusted hop is ignored.
 * This is what the emergency admin's "internal network only" rule is judged on.
 */
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
