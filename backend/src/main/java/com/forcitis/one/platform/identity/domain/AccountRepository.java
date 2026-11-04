package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public interface AccountRepository {

    Optional<UserAccount> find(UserId userId);

    /** As {@link #find}, taking the account row FOR UPDATE: after the separation advisory lock where the path takes it, before any role row lock. */
    Optional<UserAccount> findForUpdate(UserId userId);

    /** Whether the listener already decided this personnel command (handled_personnel_commands); read under the account row lock. */
    boolean isHandled(PersonnelCommandId command);

    /** Records the decision on a personnel command in the transaction of the decision; grant is the grant it opened, only for GRANT_OPENED. */
    void saveHandled(PersonnelCommandId command, UserId user, HandledOutcome outcome, @Nullable AccountRoleGrantId grant);

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

    /** The place of an account in the list order: the moment it was opened, then its id. */
    record ListPosition(Instant createdAt, UserId userId) {}

    /** At most limit accounts ordered by opening moment, then user id, after the given position; roleCode matches the grants open now; null filters match everything. */
    List<AccountListRow> list(@Nullable AccountStatus status, @Nullable RoleCode roleCode, @Nullable ListPosition after, int limit);

    /** Employment is checked by the caller. */
    Set<UserId> findEnabledHolders(RoleCode roleCode);
}
