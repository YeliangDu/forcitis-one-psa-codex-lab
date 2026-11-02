package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AccountRepository {

    Optional<UserAccount> find(UserId userId);

    /** As {@link #find}, taking the account row FOR UPDATE: after the separation advisory lock where the path takes it, before any role row lock. */
    Optional<UserAccount> findForUpdate(UserId userId);

    /** The grants open now, with the role code each belongs to. */
    List<HeldGrant> openGrants(UserId userId);

    /**
     * Opens and closes the grant rows of the account (account_role_grants is the only grant store). Takes the role rows FOR SHARE before opening, so a role
     * cannot be deleted under a grant being opened; a start is never before the end of the person's earlier rows of the role, an end never before its start.
     */
    void saveGrants(UserId userId, GrantChanges changes);

    Optional<UserId> findByEmployee(EmployeeId employeeId);

    Optional<EmployeeId> findEmployee(UserId userId);

    /** True only for the caller that created it: an employee who has an account already keeps it (atomic). Writes the account row only; its grants follow in {@link #saveGrants}. */
    boolean insertIfAbsent(UserAccount opened);

    /** Applies only to the version the account was loaded with; otherwise a DomainException (CONFLICT). Writes the status; grants go through {@link #saveGrants}. */
    void save(UserAccount account);

    void recordLogin(UserId userId, Instant at, String address);

    List<Role> findEnabledRoles(UserId userId);

    Set<RoleCode> findAssignedRoleCodes(UserId userId);

    /** Employment is checked by the caller. */
    Set<UserId> findEnabledHolders(RoleCode roleCode);
}
