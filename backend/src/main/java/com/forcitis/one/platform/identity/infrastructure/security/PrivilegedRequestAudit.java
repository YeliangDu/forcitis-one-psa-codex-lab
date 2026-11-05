package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.identity.application.ClientOrigin;
import com.forcitis.one.platform.identity.application.SessionMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** In the administrator mode every write, and every read of the authorisation data, is an audit entry (ADR-0026 section 5 item 4). */
@Configuration
class PrivilegedRequestAudit implements WebMvcConfigurer, HandlerInterceptor {

    private final AuditLog audit;
    private final ClientOrigin origin;
    private final Clock clock;

    PrivilegedRequestAudit(AuditLog audit, ClientOrigin origin, Clock clock) {
        this.audit = audit;
        this.origin = origin;
        this.clock = clock;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**").excludePathPatterns("/api/v1/platform/sessions:*");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof IdentityAuthentication identity && identity.modeAt(clock.instant()) == SessionMode.ADMINISTRATOR
                && (writes(request) || readsAuthorisationData(request))) {
            audit.recordIndependently(new AuditEntry("platform.identity.privileged.request", identity.account().orElse(null), null,
                    AuditSubject.without(AuditSubjectKind.REQUEST), RequestLabel.of(request))
                    .withTrace(origin.address(), AuditOutcome.SUCCEEDED, identity.loginMethod().auditKind()));
        }
        return true;
    }

    private static boolean writes(HttpServletRequest request) {
        return !("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod()));
    }

    private static boolean readsAuthorisationData(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/platform/roles") || path.contains("/platform/user-accounts") || path.contains("/platform/authorization-requests");
    }
}
