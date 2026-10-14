package com.forcitis.one.platform.identity.infrastructure.security;

import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * The authentication stored in the server-side session: only the user id (as the principal name), when and how the person
 * logged in. Authorities are NOT stored: they are resolved on every request from the live roles (cached for at most 60 s),
 * so a role change never waits for the session to end. Everything here is plain serialisable data.
 */
final class IdentityAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final Instant loginAt;
    private final String method;

    IdentityAuthentication(String userId, Instant loginAt, String method, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.loginAt = loginAt;
        this.method = method;
        super.setAuthenticated(true);
    }

    IdentityAuthentication withAuthorities(Collection<? extends GrantedAuthority> fresh) {
        return new IdentityAuthentication(userId, loginAt, method, fresh);
    }

    Instant loginAt() {
        return loginAt;
    }

    long userIdValue() {
        return Long.parseLong(userId);
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
