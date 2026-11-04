package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Read-only view of the grant and role version history at a past moment (ADR-0034 decision 4 items 3 to 5). */
public interface GrantHistory {

    /** A role held at the moment, as its version of that moment defined it. */
    record HeldRole(String roleCode, String roleName, String accessScope, Set<String> permissionCodes) {
        public HeldRole {
            permissionCodes = Set.copyOf(permissionCodes);
        }
    }

    /** Where the previous page of {@link #rowsOf} ended: the start and the id of its last row. */
    record RowPosition(Instant effectiveFrom, AccountRoleGrantId grantId) {
    }

    /** Every grant row of the account, open and closed, newest start first and by grant id descending after it, at most limit rows after the position. */
    List<GrantRow> rowsOf(UserId user, @Nullable RowPosition after, int limit);

    /** Empty when the moment is before every grant record of the person: the history cannot say what they held. */
    Optional<List<HeldRole>> heldAt(UserId user, Instant moment);
}
