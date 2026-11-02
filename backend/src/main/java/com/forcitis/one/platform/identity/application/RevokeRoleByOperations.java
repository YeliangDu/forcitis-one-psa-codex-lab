package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operations command, the counterpart of {@link GrantRoleByOperations}: takes a role back from an existing account and ends that person's sessions. Never opens an account. */
@Service
public class RevokeRoleByOperations {

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final DomainEvents events;
    private final SingleOperatorMarks marks;
    private final AccountGrants grants;

    RevokeRoleByOperations(AccountRepository accounts, RoleRepository roles, SessionRegistry sessions, AccessCache cache, DomainEvents events,
            SingleOperatorMarks marks, AccountGrants grants) {
        this.accounts = accounts;
        this.roles = roles;
        this.sessions = sessions;
        this.cache = cache;
        this.events = events;
        this.marks = marks;
        this.grants = grants;
    }

    /** True when the role was held and is taken. */
    @Transactional
    public boolean execute(OperationAttestation attestation, EmployeeId employeeId, String roleCode) {
        Role role = roles.findByCode(new RoleCode(roleCode == null ? "" : roleCode)).orElseThrow(() -> new DomainException(
                IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND, ErrorKind.NOT_FOUND, "no role " + roleCode));
        UserId person = accounts.findByEmployee(employeeId).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND,
                ErrorKind.NOT_FOUND, "employee " + employeeId.value() + " has no account"));
        UserAccount account = accounts.findForUpdate(person).orElseThrow(() -> new IllegalStateException("account " + person.value() + " vanished"));
        if (!account.roles().contains(role.roleId())) {
            return false;
        }
        Set<RoleId> wanted = new HashSet<>(account.roles());
        wanted.remove(role.roleId());
        grants.replace(account, wanted, AccountGrants.Origin.operations((subject, code) -> new AuditEntry("platform.identity.operations.account.role.revoked", null, null, subject,
                attestation.describe() + " role=" + code)));
        accounts.save(account);
        events.publish(account);
        sessions.revokeAll(person);
        cache.evict(person);
        marks.witnessOmitted(attestation, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, person.value()));
        return true;
    }
}
