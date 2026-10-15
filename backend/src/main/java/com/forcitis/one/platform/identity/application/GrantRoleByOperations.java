package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
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
import java.util.Optional;
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
    private final OrgDirectory directory;
    private final AccountProvisioning provisioning;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final DomainEvents events;
    private final AccountEmployees accountEmployees;
    private final AuditLog audit;

    GrantRoleByOperations(AccountRepository accounts, RoleRepository roles, OrgDirectory directory, AccountProvisioning provisioning,
            SessionRegistry sessions, AccessCache cache, DomainEvents events, AuditLog audit, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.accounts = accounts;
        this.roles = roles;
        this.directory = directory;
        this.provisioning = provisioning;
        this.sessions = sessions;
        this.cache = cache;
        this.events = events;
        this.audit = audit;
    }

    /** True when the role was added. */
    @Transactional
    public boolean execute(OperationAttestation attestation, long personId, String roleCode) {
        UserId person = new UserId(personId);
        Role role = roles.findByCode(new RoleCode(roleCode == null ? "" : roleCode)).orElseThrow(() -> new DomainException(
                IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND, ErrorKind.NOT_FOUND, "no role " + roleCode));
        Optional<UserAccount> found = accounts.find(person);
        if (found.isEmpty()) {
            directory.findEmployee(accountEmployees.employeeOf(person)).ifPresent(provisioning::ensureAccount);
            found = accounts.find(person);
        }
        UserAccount account = found.orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND, ErrorKind.NOT_FOUND,
                "user " + personId + " has no account and is not an employed person who could get one"));
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
        audit.record(new AuditEntry("platform.identity.operations.account.role.granted", null, null, "user-account:" + personId,
                attestation.describe() + " role=" + role.code().value()));
        return true;
    }
}
