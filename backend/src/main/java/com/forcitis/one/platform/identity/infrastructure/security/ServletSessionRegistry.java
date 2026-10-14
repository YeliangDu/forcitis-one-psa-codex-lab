package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.application.LoginMethod;
import com.forcitis.one.platform.identity.application.SessionRegistry;
import com.forcitis.one.platform.identity.application.SessionSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The principal name index (the user id) lets every session of a user be found and deleted at once. */
@Component
class ServletSessionRegistry implements SessionRegistry {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SessionSettings settings;
    private final Clock clock;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SessionAuthenticationStrategy rotation;

    ServletSessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository, SessionSettings settings, Clock clock,
            CookieCsrfTokenRepository csrfTokenRepository) {
        this.sessionRepository = sessionRepository;
        this.settings = settings;
        this.clock = clock;
        this.rotation = new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Override
    public Instant start(UserId userId, LoginMethod method) {
        ServletRequestAttributes attributes = currentRequest();
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        Instant now = clock.instant();
        IdentityAuthentication authentication = new IdentityAuthentication(String.valueOf(userId.value()), now, method.name(), List.of());
        HttpSession session = request.getSession(true);
        rotation.onAuthentication(authentication, request, response);
        session = request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, String.valueOf(userId.value()));
        session.setMaxInactiveInterval((int) settings.idleTimeout().toSeconds());
        return now.plus(settings.absoluteTimeout());
    }

    @Override
    public void end() {
        HttpSession session = currentRequest().getRequest().getSession(false);
        SecurityContextHolder.clearContext();
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    public void revokeAll(UserId userId) {
        sessionRepository.findByPrincipalName(String.valueOf(userId.value())).keySet().forEach(sessionRepository::deleteById);
    }

    private static ServletRequestAttributes currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet && servlet.getResponse() != null) {
            return servlet;
        }
        throw new IllegalStateException("no servlet request in progress: a session can only start inside a web request");
    }
}
