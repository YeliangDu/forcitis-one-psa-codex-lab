package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.Objects;
import java.util.Set;

/** allowedRoleCodes is the user scope (empty = nobody) and can never include an administrator role. */
public record TicketIssuer(String issuerCode, String kid, String keyReference, boolean enabled, Set<RoleCode> allowedRoleCodes) {

    public TicketIssuer {
        Objects.requireNonNull(issuerCode, "issuerCode");
        Objects.requireNonNull(kid, "kid");
        Objects.requireNonNull(keyReference, "keyReference");
        allowedRoleCodes = Set.copyOf(allowedRoleCodes);
    }
}
