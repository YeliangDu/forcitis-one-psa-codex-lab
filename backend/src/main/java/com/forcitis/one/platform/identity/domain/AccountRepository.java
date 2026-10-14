package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AccountRepository {

    Optional<UserAccount> find(UserId userId);

    /** Creates an enabled account unless one exists; true only for the caller that created it (atomic). */
    boolean createIfAbsent(UserId userId);

    void changeStatus(UserId userId, AccountStatus status);

    void recordLogin(UserId userId, Instant at, String address);

    /** Roles of the account that are ENABLED, with their permissions (RULE-343). */
    List<Role> findEnabledRoles(UserId userId);

    /** All role codes assigned to the account, enabled or not (administration view). */
    Set<RoleCode> findAssignedRoleCodes(UserId userId);

    /** Adds one link; no-op when present. */
    void grantRole(UserId userId, RoleId roleId);

    /** Replaces the whole set: adds the missing, removes the rest (RULE-346). */
    void replaceRoles(UserId userId, Set<RoleId> roleIds);

    /** Accounts that are ENABLED and hold the ENABLED role with this code. Employment is checked by the caller. */
    Set<UserId> findEnabledHolders(RoleCode roleCode);
}
