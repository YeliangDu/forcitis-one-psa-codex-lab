package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.RoleVersionId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a request was made against (ADR-0034 decision 2 item 5): the account version for an account request, and the current version of every related role
 * (for an account request each role of the wanted set and of the set held then; for a role change the role itself; none for a new role).
 */
public record AuthorizationBase(@Nullable Long accountVersion, List<RoleVersionId> roleVersions) {

    public static final AuthorizationBase NONE = new AuthorizationBase(null, List.of());

    public AuthorizationBase {
        roleVersions = List.copyOf(roleVersions);
    }
}
