package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.domain.RoleChange;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleDraft;
import com.forcitis.one.platform.identity.domain.RoleKind;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permissions are resolved on every request, so a change is live once the cache is evicted. Nobody changes the definition of a role
 * they hold (I-12); giving a role a sensitive permission waits for a second person.
 */
@Service
public class UpdateRole {

    /** The fixed reason of a new version (ADR-0034-IMPL ruling A-5: the application writes it, the request body has none). */
    static final String REASON = "role management: role revised";

    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final AccessCache cache;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final AccountRepository accounts;
    private final AuthorizationGate gate;
    private final AuditLog audit;

    UpdateRole(RoleRepository roles, PrivilegeCeiling ceiling, AccessCache cache, DomainEvents events, CurrentUser currentUser,
            AccountRepository accounts, AuthorizationGate gate, AuditLog audit) {
        this.roles = roles;
        this.ceiling = ceiling;
        this.cache = cache;
        this.events = events;
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.gate = gate;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<RoleView> execute(RoleId roleId, ExpectedVersion expected, SaveRoleCommand command) {
        Optional<Role> existing = roles.findById(roleId);
        if (existing.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
        }
        expected.check(existing.get().version());
        var caller = currentUser.userId();
        if (existing.get().kind() != RoleKind.SYSTEM && accounts.findAssignedRoleCodes(caller).contains(existing.get().code())) {
            audit.recordIndependently(new AuditEntry("platform.identity.grant.refused", caller, null, AuditSubject.of(AuditSubjectKind.ROLE, roleId.value()),
                    "nobody changes the definition of a role they hold"));
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_OWN_ROLE_DEFINITION_FORBIDDEN);
        }
        try {
            Role role = existing.get();
            Set<String> before = new HashSet<>(role.permissionCodes());
            AccessScopeKind scope = command.accessScope() != null ? command.accessScope() : role.accessScope();
            role.revise(command.name(), scope, command.permissionCodes());
            role.declareEnabled(command.enabled());
            Set<String> added = new HashSet<>(SensitivePermissions.within(role.permissionCodes()));
            added.removeAll(before);
            if (!added.isEmpty()) {
                if (!ceiling.mayRequest(role.permissionCodes(), role.administrative())) {
                    return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
                }
                return new AdminOutcome.Awaiting<>(gate.openUpdateRole(caller, role.roleId(),
                        new RoleDraft(role.code().value(), command.name(), command.enabled(), command.accessScope(), command.permissionCodes())).value().toString());
            }
            if (!ceiling.mayGrant(role.permissionCodes(), role.administrative())) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            return store(role, RoleChange.manual(caller, REASON, null));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }

    /** The request's author made the change; the version names the request a second person confirmed. */
    @Transactional
    AdminOutcome<RoleView> applyConfirmed(RoleId roleId, SaveRoleCommand command, UserId requestedBy, AuthorizationRequestId requestId) {
        Optional<Role> existing = roles.findById(roleId);
        if (existing.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
        }
        try {
            Role role = existing.get();
            AccessScopeKind scope = command.accessScope() != null ? command.accessScope() : role.accessScope();
            role.revise(command.name(), scope, command.permissionCodes());
            role.declareEnabled(command.enabled());
            return store(role, RoleChange.manual(requestedBy, REASON, requestId));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }

    private AdminOutcome<RoleView> store(Role role, RoleChange change) {
        roles.update(role, change);
        events.publish(role);
        cache.evictAll();
        return new AdminOutcome.Done<>(RoleView.of(role));
    }
}
