package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
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

/**
 * Operations command for the first holders: super_admin for the first two administrators and again after a lock-out, and the
 * business roles of the first people (the business administrator, the organisation data administrator). The account is opened for an
 * employed person who has none yet. Granting a role already held changes nothing.
 */
@Service
public class GrantRoleByOperations {

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final AccountProvisioning provisioning;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final DomainEvents events;
    private final AuditLog audit;

    GrantRoleByOperations(AccountRepository accounts, RoleRepository roles, AccountProvisioning provisioning,
            SessionRegistry sessions, AccessCache cache, DomainEvents events, AuditLog audit) {
        this.accounts = accounts;
        this.roles = roles;
        this.provisioning = provisioning;
        this.sessions = sessions;
        this.cache = cache;
        this.events = events;
        this.audit = audit;
    }

    /** True when the role was added. */
    @Transactional
    public boolean execute(OperationAttestation attestation, EmployeeId employeeId, String roleCode) {
        Role role = roles.findByCode(new RoleCode(roleCode == null ? "" : roleCode)).orElseThrow(() -> new DomainException(
                IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND, ErrorKind.NOT_FOUND, "no role " + roleCode));
        UserId person = provisioning.accountForOperations(employeeId);
        UserAccount account = accounts.find(person).orElseThrow(() -> new IllegalStateException("account " + person.value() + " vanished"));
        if (account.roles().contains(role.roleId())) {
            return false;
        }
        Set<RoleId> wanted = new HashSet<>(account.roles());
        wanted.add(role.roleId());
        account.replaceRoles(wanted);
        accounts.save(account);
        events.publish(account);
        sessions.revokeAll(person);
        cache.evict(person);
        audit.record(new AuditEntry("platform.identity.operations.account.role.granted", null, null, "user-account:" + person.value(),
                attestation.describe() + " role=" + role.code().value()));
        return true;
    }
}
