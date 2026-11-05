package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.identity.application.SessionKind;
import com.forcitis.one.platform.identity.application.SessionMode;
import com.forcitis.one.platform.identity.application.SessionPrincipal;
import com.forcitis.one.platform.identity.application.SessionSettings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Not a Spring bean on purpose: Boot would also register a bean Filter in the servlet container. */
final class SessionGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionGuardFilter.class);
    private static final String EXPIRY_AUDITED = SessionGuardFilter.class.getName() + ".expiryAudited";

    private final CachedSessionPrincipals principals;
    private final SessionSettings settings;
    private final Clock clock;
    private final AuditLog audit;

    SessionGuardFilter(CachedSessionPrincipals principals, SessionSettings settings, Clock clock, AuditLog audit) {
        this.principals = principals;
        this.settings = settings;
        this.clock = clock;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof IdentityAuthentication identity && identity.account().isEmpty()) {
            log.error("ALERT platform.identity a session whose principal is not an account id was ended");
            terminate(request);
        } else if (authentication instanceof IdentityAuthentication identity) {
            UserId account = identity.account().orElseThrow();
            Instant now = clock.instant();
            auditNaturalExpiry(identity, account, request, now);
            if (identity.loginAt().plus(settings.absoluteTimeout()).isBefore(now) || emergencySessionOver(identity, now)) {
                terminate(request);
            } else {
                SessionKind kind = identity.kindAt(now);
                SessionPrincipal principal = principals.get(account, kind);
                if (principal.isActive()) {
                    HttpSession session = request.getSession(false);
                    boolean unconfirmed = session == null || !Boolean.TRUE.equals(session.getAttribute(ServletSessionRegistry.CONFIRMED_ATTRIBUTE));
                    SecurityContext refreshed = SecurityContextHolder.createEmptyContext();
                    List<SimpleGrantedAuthority> granted = new ArrayList<>(principal.authorities().stream().map(SimpleGrantedAuthority::new).toList());
                    if (kind.mode() == SessionMode.ORDINARY) {
                        granted.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
                    }
                    refreshed.setAuthentication(identity.withAuthorities(unconfirmed ? List.of() : granted, !unconfirmed));
                    SecurityContextHolder.setContext(refreshed);
                } else {
                    terminate(request);
                }
            }
        }
        chain.doFilter(request, response);
    }

    /** Without it the end of the mode would leave no trace: the exit audit is written once per mode, by the first request after the end. */
    private void auditNaturalExpiry(IdentityAuthentication identity, UserId account, HttpServletRequest request, Instant now) {
        Instant until = identity.administratorModeUntil();
        HttpSession session = request.getSession(false);
        if (until == null || now.isBefore(until) || session == null || until.equals(session.getAttribute(EXPIRY_AUDITED))) {
            return;
        }
        session.setAttribute(EXPIRY_AUDITED, until);
        audit.recordIndependently(new AuditEntry("platform.identity.administrator-mode.exited", account, null,
                AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, account.value()), "expired at " + until).withTrace(request.getRemoteAddr(), AuditOutcome.SUCCEEDED,
                identity.loginMethod().auditKind()));
        log.error("ALERT platform.identity administrator mode of user {} expired at {}", account.value(), until);
    }

    private static boolean emergencySessionOver(IdentityAuthentication identity, Instant now) {
        return identity.emergency() && identity.modeAt(now) != SessionMode.ADMINISTRATOR;
    }

    private static void terminate(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
