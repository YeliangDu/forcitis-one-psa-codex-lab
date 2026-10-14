package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AccountRepository {

    Optional<UserAccount> find(UserId userId);

    /** True only for the caller that created it (atomic). */
    boolean createIfAbsent(UserId userId);

    /** Applies only to the status and version the account was loaded with; otherwise a DomainException (CONFLICT). */
    void save(UserAccount account);

    void recordLogin(UserId userId, Instant at, String address);

    List<Role> findEnabledRoles(UserId userId);

    /** Enabled or not (administration view). */
    Set<RoleCode> findAssignedRoleCodes(UserId userId);

    void grantRole(UserId userId, RoleId roleId);

    void replaceRoles(UserId userId, Set<RoleId> roleIds);

    /** Employment is checked by the caller. */
    Set<UserId> findEnabledHolders(RoleCode roleCode);
}
