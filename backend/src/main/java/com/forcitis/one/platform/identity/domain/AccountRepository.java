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

    Optional<UserId> findByEmployee(EmployeeId employeeId);

    Optional<EmployeeId> findEmployee(UserId userId);

    /** True only for the caller that created it: an employee who has an account already keeps it (atomic). */
    boolean insertIfAbsent(UserAccount opened);

    /** Applies only to the version the account was loaded with; otherwise a DomainException (CONFLICT). Writes status and roles. */
    void save(UserAccount account);

    void recordLogin(UserId userId, Instant at, String address);

    List<Role> findEnabledRoles(UserId userId);

    Set<RoleCode> findAssignedRoleCodes(UserId userId);

    /** Employment is checked by the caller. */
    Set<UserId> findEnabledHolders(RoleCode roleCode);
}
