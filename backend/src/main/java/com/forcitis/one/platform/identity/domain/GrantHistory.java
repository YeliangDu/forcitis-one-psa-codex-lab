package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only view of the grant and role version history at a past moment (ADR-0034 decision 4 items 3 to 5). */
public interface GrantHistory {

    /** A role held at the moment, as its version of that moment defined it. */
    record HeldRole(String roleCode, String roleName, String accessScope, Set<String> permissionCodes) {
        public HeldRole {
            permissionCodes = Set.copyOf(permissionCodes);
        }
    }

    /** Whether the person holds, or held for a non-empty span, a grant that was opened at or after the moment: the trace of a reinstatement already handled. */
    boolean openedFrom(UserId user, Instant moment);

    /** Empty when the moment is before every grant record of the person: the history cannot say what they held. */
    Optional<List<HeldRole>> heldAt(UserId user, Instant moment);
}
