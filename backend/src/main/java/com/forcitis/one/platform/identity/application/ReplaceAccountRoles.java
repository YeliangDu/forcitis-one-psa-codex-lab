package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A role change takes effect at once: the account's sessions are deleted and the cache evicted. Nobody changes their own roles (I-12);
 * a change that gives or takes a sensitive role waits for a second person.
 */
@Service
public class ReplaceAccountRoles {

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final SessionRevocation revocation;
    private final AccessCache cache;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final AuthorizationGate gate;
    private final AuditLog audit;

    ReplaceAccountRoles(AccountRepository accounts, RoleRepository roles, PrivilegeCeiling ceiling, SessionRevocation revocation,
            AccessCache cache, DomainEvents events, CurrentUser currentUser, AuthorizationGate gate, AuditLog audit) {
        this.accounts = accounts;
        this.roles = roles;
        this.ceiling = ceiling;
        this.revocation = revocation;
        this.cache = cache;
        this.events = events;
        this.currentUser = currentUser;
        this.gate = gate;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<Long> execute(UserId userId, Set<String> roleCodes, ExpectedVersion expected) {
        Optional<UserAccount> found = accounts.find(userId);
        if (found.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND);
        }
        UserAccount account = found.get();
        expected.check(account.version());
        UserId caller = currentUser.userId();
        if (account.userId().equals(caller)) {
            audit.recordIndependently(new AuditEntry("platform.identity.grant.refused", caller, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                    "nobody changes their own roles"));
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN);
        }
        List<Role> wanted = new ArrayList<>();
        for (String code : roleCodes) {
            Optional<Role> role = roles.findByCode(new RoleCode(code));
            if (role.isEmpty()) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
            }
            wanted.add(role.get());
        }
        Set<RoleCode> alreadyHeld = accounts.findAssignedRoleCodes(account.userId());
        if (touchesSensitiveRole(wanted, alreadyHeld)) {
            Set<String> requested = new HashSet<>();
            boolean administrativeRole = false;
            for (Role role : wanted) {
                if (!alreadyHeld.contains(role.code())) {
                    requested.addAll(role.permissionCodes());
                    administrativeRole |= role.administrative();
                }
            }
            if (!ceiling.mayRequest(requested, administrativeRole)) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            return new AdminOutcome.Awaiting<>(gate.openReplaceRoles(caller, account.userId(), roleCodes).value().toString());
        }
        Set<String> wantedPermissions = new HashSet<>();
        boolean administrative = false;
        for (Role role : wanted) {
            if (!alreadyHeld.contains(role.code())) {
                wantedPermissions.addAll(role.permissionCodes());
                administrative |= role.administrative();
            }
        }
        if (!ceiling.mayGrant(wantedPermissions, administrative)) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
        }
        replace(account, wanted);
        return new AdminOutcome.Done<>(account.version());
    }

    /** The second person agreed: applied without the single-person checks, which the confirmation replaces. */
    @Transactional
    AdminOutcome<Long> applyConfirmed(UserId userId, Set<String> roleCodes) {
        Optional<UserAccount> found = accounts.find(userId);
        if (found.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND);
        }
        List<Role> wanted = new ArrayList<>();
        for (String code : roleCodes) {
            Optional<Role> role = roles.findByCode(new RoleCode(code));
            if (role.isEmpty()) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
            }
            wanted.add(role.get());
        }
        replace(found.get(), wanted);
        return new AdminOutcome.Done<>(found.get().version());
    }

    private boolean touchesSensitiveRole(List<Role> wanted, Set<RoleCode> alreadyHeld) {
        Set<RoleCode> wantedCodes = new HashSet<>();
        for (Role role : wanted) {
            wantedCodes.add(role.code());
            if (!alreadyHeld.contains(role.code()) && SensitivePermissions.roleIsSensitive(role)) {
                return true;
            }
        }
        for (RoleCode held : alreadyHeld) {
            if (!wantedCodes.contains(held) && roles.findByCode(held).map(SensitivePermissions::roleIsSensitive).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private void replace(UserAccount account, List<Role> wanted) {
        Set<RoleId> wantedIds = new HashSet<>();
        wanted.forEach(role -> wantedIds.add(role.roleId()));
        account.replaceRoles(wantedIds);
        if (account.hasPendingEvents()) {
            accounts.save(account);
            events.publish(account);
            revocation.revokeAll(account.userId(), "the roles of the account were replaced");
            cache.evict(account.userId());
        }
    }
}
