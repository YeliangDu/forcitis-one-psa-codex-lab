package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.identity.application.ClientOrigin;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Every refusal of a signed-in person is a trail entry (ADR-0026 section 6 item 5): by method security and by the request rules. The
 * same person and resource are written at most once a minute, so a script knocking on a door does not flood the trail.
 */
@Component
class AccessDeniedAudit implements AuthorizationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AccessDeniedAudit.class);
    private static final Duration QUIET = Duration.ofMinutes(1);

    private final AuditLog audit;
    private final ClientOrigin origin;
    private final Clock clock;
    private final Map<String, Instant> lastWritten = new ConcurrentHashMap<>();

    AccessDeniedAudit(AuditLog audit, ClientOrigin origin, Clock clock) {
        this.audit = audit;
        this.origin = origin;
        this.clock = clock;
    }

    @Override
    public <T> void publishAuthorizationEvent(Supplier<Authentication> authentications, T object, AuthorizationResult result) {
        if (result == null || result.isGranted()) {
            return;
        }
        Authentication authentication = authentications.get();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) {
            return;
        }
        UserId actor;
        try {
            actor = new UserId(Long.parseLong(authentication.getName()));
        } catch (NumberFormatException notAPerson) {
            return;
        }
        String resource = resourceOf(object);
        Instant now = clock.instant();
        Instant previous = lastWritten.get(actor.value() + " " + resource);
        if (previous != null && previous.plus(QUIET).isAfter(now)) {
            return;
        }
        lastWritten.put(actor.value() + " " + resource, now);
        try {
            audit.recordIndependently(new AuditEntry("platform.identity.access.denied", actor, null, resource, "access refused")
                    .withTrace(origin.address(), AuditOutcome.FAILED, null));
        } catch (RuntimeException failed) {
            log.error("The refusal of user {} for {} could not be written to the audit trail: {}", actor.value(), resource, failed.toString());
        }
    }

    private static String resourceOf(Object object) {
        if (object instanceof MethodInvocation invocation) {
            return "operation:" + invocation.getMethod().getDeclaringClass().getSimpleName() + "." + invocation.getMethod().getName();
        }
        if (object instanceof RequestAuthorizationContext context) {
            return "request:" + context.getRequest().getMethod() + " " + context.getRequest().getRequestURI();
        }
        return "resource:unknown";
    }
}
