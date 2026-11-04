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
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AuthorizationBase;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleDefinition;
import com.forcitis.one.platform.identity.domain.RoleDraft;
import com.forcitis.one.platform.identity.domain.RoleKind;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permissions are resolved on every request, so a change is live once the cache is evicted. Nobody changes the definition of a role
 * they hold (I-12); giving a role a sensitive permission, or widening what a two-person role covers, waits for a second person; a two-person role keeps a scope that does not follow
 * the holder's job and names its custom org units without descendants (ADR-0032 section 3, ADR-0044 section 1).
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
            RoleDefinition definitionBefore = role.definition();
            revise(role, command);
            Optional<IdentityError> scopeRefused = refuseScope(caller, role);
            if (scopeRefused.isPresent()) {
                return new AdminOutcome.Refused<>(scopeRefused.get());
            }
            Set<String> added = new HashSet<>(SensitivePermissions.within(role.permissionCodes()));
            added.removeAll(before);
            boolean widened = !SensitivePermissions.within(role.permissionCodes()).isEmpty() && SensitivePermissions.addsReach(definitionBefore, role.definition());
            if (!added.isEmpty() || widened) {
                if (!ceiling.mayRequest(role.permissionCodes(), role.administrative())) {
                    return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
                }
                return new AdminOutcome.Awaiting<>(gate.openUpdateRole(caller, role.roleId(), draft(role, command),
                        new AuthorizationBase(null, List.of(Objects.requireNonNull(existing.get().roleVersionId())))).value().toString());
            }
            if (!ceiling.mayGrant(role.permissionCodes(), role.administrative())) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            return store(role, RoleChange.manual(caller, REASON, null));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }

    /** The request's author made the change; the version names the request a second person confirmed. The scope is checked again here. */
    @Transactional
    AdminOutcome<RoleView> applyConfirmed(RoleId roleId, SaveRoleCommand command, UserId requestedBy, AuthorizationRequestId requestId) {
        Optional<Role> existing = roles.findById(roleId);
        if (existing.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
        }
        try {
            Role role = existing.get();
            revise(role, command);
            Optional<IdentityError> scopeRefused = refuseScope(requestedBy, role);
            if (scopeRefused.isPresent()) {
                return new AdminOutcome.Refused<>(scopeRefused.get());
            }
            return store(role, RoleChange.manual(requestedBy, REASON, requestId));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }

    /** accessScope null keeps the role's scope kind, customOrgUnits null keeps its units (one-coordinator ruling for #728). */
    private static void revise(Role role, SaveRoleCommand command) {
        AccessScopeKind scope = command.accessScope() != null ? command.accessScope() : role.accessScope();
        role.revise(command.name(), scope, command.permissionCodes(), command.customOrgUnits());
        role.declareEnabled(command.enabled());
    }

    /** Refuses a scope a two-person role may not have, with an audit entry naming the reason (F1-01; F-1 card C4). */
    private Optional<IdentityError> refuseScope(UserId by, Role role) {
        Optional<String> reason = SensitivePermissions.scopeRefusal(role.definition());
        reason.ifPresent(why -> audit.recordIndependently(new AuditEntry("platform.identity.grant.refused", by, null,
                AuditSubject.of(AuditSubjectKind.ROLE, role.roleId().value()), RoleScopeRefusal.detail(why, role))));
        return reason.map(why -> IdentityError.PLATFORM_IDENTITY_ROLE_SCOPE_NOT_ALLOWED);
    }

    /**
     * The definition the second person confirms. A CUSTOM_ORG_UNITS role names its units in the draft even when the request kept the scope
     * kind, so the request row can carry them (the request's units hang only on a CUSTOM_ORG_UNITS request, F-1 card constraint 8).
     */
    private static RoleDraft draft(Role role, SaveRoleCommand command) {
        AccessScopeKind scope = command.accessScope() != null || role.accessScope() != AccessScopeKind.CUSTOM_ORG_UNITS ? command.accessScope()
                : AccessScopeKind.CUSTOM_ORG_UNITS;
        return new RoleDraft(role.code().value(), command.name(), command.enabled(), scope, command.permissionCodes(), RoleDraft.unitIdsOf(role.customOrgUnits()));
    }

    private AdminOutcome<RoleView> store(Role role, RoleChange change) {
        roles.update(role, change);
        events.publish(role);
        cache.evictAll();
        return new AdminOutcome.Done<>(RoleView.of(role));
    }
}
