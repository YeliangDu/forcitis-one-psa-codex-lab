package com.forcitis.one.platform.identity.application;

import java.util.Set;

/** Eligibility plus the permission codes of the person's enabled roles; authorities are empty unless ACTIVE. */
public record SessionPrincipal(Eligibility eligibility, Set<String> authorities) {

    public SessionPrincipal {
        authorities = Set.copyOf(authorities);
    }

    public boolean isActive() {
        return eligibility == Eligibility.ACTIVE;
    }
}
