package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.application.LoginMethod;
import com.forcitis.one.platform.identity.application.SessionKind;
import com.forcitis.one.platform.identity.application.SessionMode;
import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authorities are not stored: they are resolved on every request from the live roles. The administrator mode ends by itself at
 * administratorModeUntil and is never renewed.
 */
final class IdentityAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 2L;

    private final String userId;
    private final Instant loginAt;
    private final String method;
    private final Instant administratorModeUntil;

    IdentityAuthentication(String userId, Instant loginAt, String method, Instant administratorModeUntil,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.loginAt = loginAt;
        this.method = method;
        this.administratorModeUntil = administratorModeUntil;
        super.setAuthenticated(true);
    }

    IdentityAuthentication withAuthorities(Collection<? extends GrantedAuthority> fresh) {
        return new IdentityAuthentication(userId, loginAt, method, administratorModeUntil, fresh);
    }

    IdentityAuthentication withAdministratorModeUntil(Instant until) {
        return new IdentityAuthentication(userId, loginAt, method, until, getAuthorities());
    }

    Instant loginAt() {
        return loginAt;
    }

    long userIdValue() {
        return Long.parseLong(userId);
    }

    LoginMethod loginMethod() {
        return LoginMethod.valueOf(method);
    }

    Instant administratorModeUntil() {
        return administratorModeUntil;
    }

    boolean emergency() {
        return loginMethod() == LoginMethod.EMERGENCY;
    }

    SessionMode modeAt(Instant now) {
        return administratorModeUntil != null && now.isBefore(administratorModeUntil) ? SessionMode.ADMINISTRATOR : SessionMode.ORDINARY;
    }

    SessionKind kindAt(Instant now) {
        SessionMode mode = modeAt(now);
        return new SessionKind(mode, mode == SessionMode.ADMINISTRATOR && emergency());
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
