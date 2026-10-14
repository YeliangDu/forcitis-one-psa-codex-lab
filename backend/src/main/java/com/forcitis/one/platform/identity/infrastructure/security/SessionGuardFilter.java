package com.forcitis.one.platform.identity.infrastructure.security;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Not a Spring bean on purpose: Boot would also register a bean Filter in the servlet container. */
final class SessionGuardFilter extends OncePerRequestFilter {

    private final CachedSessionPrincipals principals;
    private final SessionSettings settings;
    private final Clock clock;

    SessionGuardFilter(CachedSessionPrincipals principals, SessionSettings settings, Clock clock) {
        this.principals = principals;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof IdentityAuthentication identity) {
            Instant now = clock.instant();
            if (identity.loginAt().plus(settings.absoluteTimeout()).isBefore(now) || emergencySessionOver(identity, now)) {
                terminate(request);
            } else {
                SessionKind kind = identity.kindAt(now);
                SessionPrincipal principal = principals.get(identity.userIdValue(), kind);
                if (principal.isActive()) {
                    SecurityContext refreshed = SecurityContextHolder.createEmptyContext();
                    refreshed.setAuthentication(identity.withAuthorities(
                            principal.authorities().stream().map(SimpleGrantedAuthority::new).toList()));
                    SecurityContextHolder.setContext(refreshed);
                } else {
                    terminate(request);
                }
            }
        }
        chain.doFilter(request, response);
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
