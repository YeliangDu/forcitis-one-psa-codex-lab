package com.forcitis.one.platform.identity.infrastructure.security;

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

/**
 * Runs right after the session's security context is loaded, on every request that carries a login session:
 * the absolute cap (24 h by default) ends the session even if it is active; and the account / employment state is checked
 * (cached at most 60 s, evicted on change) - a disabled account or a separated employee loses the session on the very next
 * request, even if no event reached us (RULE-326/327/338). Authorities are refreshed from the live roles. Not a Spring bean on
 * purpose: Boot would also register a bean Filter in the servlet container.
 */
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
            if (identity.loginAt().plus(settings.absoluteTimeout()).isBefore(now)) {
                terminate(request);
            } else {
                SessionPrincipal principal = principals.get(identity.userIdValue());
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

    private static void terminate(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
