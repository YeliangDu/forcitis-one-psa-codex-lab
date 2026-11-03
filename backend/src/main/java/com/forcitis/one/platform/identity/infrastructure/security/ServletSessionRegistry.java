package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.application.AdministratorModeSettings;
import com.forcitis.one.platform.identity.application.LoginMethod;
import com.forcitis.one.platform.identity.application.SessionContext;
import com.forcitis.one.platform.identity.application.SessionMode;
import com.forcitis.one.platform.identity.application.SessionRegistry;
import com.forcitis.one.platform.identity.application.SessionSettings;
import com.forcitis.one.platform.identity.application.StartedSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
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
class ServletSessionRegistry implements SessionRegistry, SessionContext {

    static final String CONFIRMED_ATTRIBUTE = "identityConfirmed";

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SessionSettings settings;
    private final AdministratorModeSettings administratorMode;
    private final Clock clock;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SessionAuthenticationStrategy rotation;

    ServletSessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository, SessionSettings settings,
            AdministratorModeSettings administratorMode, Clock clock, CookieCsrfTokenRepository csrfTokenRepository) {
        this.sessionRepository = sessionRepository;
        this.settings = settings;
        this.administratorMode = administratorMode;
        this.clock = clock;
        this.rotation = new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Override
    public StartedSession start(UserId userId, LoginMethod method, boolean identityConfirmed) {
        ServletRequestAttributes attributes = currentRequest();
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        Instant now = clock.instant();
        Instant administratorUntil = method == LoginMethod.EMERGENCY ? now.plus(administratorMode.duration()) : null;
        IdentityAuthentication authentication = new IdentityAuthentication(String.valueOf(userId.value()), now, method.name(),
                administratorUntil, List.of(), identityConfirmed);
        request.getSession(true);
        rotation.onAuthentication(authentication, request, response);
        HttpSession session = request.getSession(true);
        store(authentication, request, response);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, String.valueOf(userId.value()));
        session.setAttribute(CONFIRMED_ATTRIBUTE, identityConfirmed);
        session.setMaxInactiveInterval((int) settings.idleTimeout().toSeconds());
        return new StartedSession(now.plus(settings.absoluteTimeout()), administratorUntil);
    }

    @Override
    public boolean identityConfirmed() {
        HttpSession session = currentRequest().getRequest().getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(CONFIRMED_ATTRIBUTE));
    }

    @Override
    public void confirmIdentity() {
        ServletRequestAttributes attributes = currentRequest();
        HttpServletRequest request = attributes.getRequest();
        if (request.getSession(false) == null) {
            return;
        }
        rotation.onAuthentication(SecurityContextHolder.getContext().getAuthentication(), request, attributes.getResponse());
        request.getSession(false).setAttribute(CONFIRMED_ATTRIBUTE, true);
    }

    @Override
    public Instant beginAdministratorMode() {
        ServletRequestAttributes attributes = currentRequest();
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        IdentityAuthentication current = currentAuthentication();
        Instant until = clock.instant().plus(administratorMode.duration());
        IdentityAuthentication elevated = current.withAdministratorModeUntil(until);
        rotation.onAuthentication(elevated, request, response);
        store(elevated, request, response);
        return until;
    }

    @Override
    public void endAdministratorMode() {
        ServletRequestAttributes attributes = currentRequest();
        IdentityAuthentication ordinary = currentAuthentication().withAdministratorModeUntil(null);
        rotation.onAuthentication(ordinary, attributes.getRequest(), attributes.getResponse());
        store(ordinary, attributes.getRequest(), attributes.getResponse());
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

    @Override
    public SessionMode mode() {
        return currentAuthentication().modeAt(clock.instant());
    }

    @Override
    public Optional<SessionMode> modeIfSignedIn() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof IdentityAuthentication identity
                ? Optional.of(identity.modeAt(clock.instant())) : Optional.empty();
    }

    @Override
    public Optional<Instant> administratorModeExpiresAt() {
        IdentityAuthentication current = currentAuthentication();
        return current.modeAt(clock.instant()) == SessionMode.ADMINISTRATOR ? Optional.of(current.administratorModeUntil()) : Optional.empty();
    }

    @Override
    public LoginMethod loginMethod() {
        return currentAuthentication().loginMethod();
    }

    private void store(IdentityAuthentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private static IdentityAuthentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof IdentityAuthentication identity) {
            return identity;
        }
        throw new DomainException(IdentityError.PLATFORM_IDENTITY_NOT_SIGNED_IN, ErrorKind.UNAUTHENTICATED, "no signed-in session");
    }

    private static ServletRequestAttributes currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet && servlet.getResponse() != null) {
            return servlet;
        }
        throw new IllegalStateException("no servlet request in progress: a session can only start inside a web request");
    }
}
