package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleChange;
import com.forcitis.one.platform.identity.domain.RoleDraft;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.RoleViolation;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A role that carries a sensitive permission is created only after a second person confirmed it, and only with a scope a two-person role
 * may have (ADR-0032 section 3, ADR-0044 section 1): otherwise 422 and an audited refusal, without a request.
 */
@Service
public class CreateRole {

    /** The fixed reason of the first version (ADR-0034-IMPL ruling A-5: the application writes it, the request body has none). */
    static final String REASON = "role management: role created";

    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final AccessCache cache;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final AuthorizationGate gate;
    private final IdGenerator ids;
    private final AuditLog audit;

    CreateRole(RoleRepository roles, PrivilegeCeiling ceiling, AccessCache cache, DomainEvents events, CurrentUser currentUser,
            AuthorizationGate gate, IdGenerator ids, AuditLog audit) {
        this.ids = ids;
        this.roles = roles;
        this.ceiling = ceiling;
        this.cache = cache;
        this.events = events;
        this.currentUser = currentUser;
        this.gate = gate;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<RoleView> execute(SaveRoleCommand command) {
        try {
            Role role = build(command);
            Optional<IdentityError> scopeRefused = refuseScope(currentUser.userId(), role);
            if (scopeRefused.isPresent()) {
                return new AdminOutcome.Refused<>(scopeRefused.get());
            }
            if (!SensitivePermissions.within(role.permissionCodes()).isEmpty()) {
                if (!ceiling.mayRequest(role.permissionCodes(), false)) {
                    return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
                }
                return new AdminOutcome.Awaiting<>(gate.openCreateRole(currentUser.userId(), draft(command, role)).value().toString());
            }
            if (!ceiling.mayGrant(role.permissionCodes(), false)) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            return store(role, RoleChange.manual(currentUser.userId(), REASON, null));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }

    /** The request's author made the change; the version names the request a second person confirmed. The scope is checked again here. */
    @Transactional
    AdminOutcome<RoleView> applyConfirmed(SaveRoleCommand command, UserId requestedBy, AuthorizationRequestId requestId) {
        try {
            Role role = build(command);
            Optional<IdentityError> scopeRefused = refuseScope(requestedBy, role);
            if (scopeRefused.isPresent()) {
                return new AdminOutcome.Refused<>(scopeRefused.get());
            }
            return store(role, RoleChange.manual(requestedBy, REASON, requestId));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }

    private Role build(SaveRoleCommand command) {
        if (command.code() == null || command.code().isBlank()) {
            throw new RoleRuleException(RoleViolation.INVALID_CODE);
        }
        Role role = Role.newCustom(new RoleId(ids.next()), new RoleCode(command.code().strip()), command.name(), command.permissionCodes());
        if (command.accessScope() != null || command.customOrgUnits() != null) {
            role.revise(role.name(), command.accessScope() != null ? command.accessScope() : role.accessScope(), role.permissionCodes(), command.customOrgUnits());
        }
        role.declareEnabled(command.enabled());
        return role;
    }

    /** Refuses a scope a two-person role may not have, with an audit entry naming the reason (F1-01; F-1 card C4). */
    private Optional<IdentityError> refuseScope(UserId by, Role role) {
        Optional<String> reason = SensitivePermissions.scopeRefusal(role.definition());
        reason.ifPresent(why -> audit.recordIndependently(new AuditEntry("platform.identity.grant.refused", by, null,
                AuditSubject.of(AuditSubjectKind.ROLE, role.roleId().value()), RoleScopeRefusal.detail(why, role))));
        return reason.map(why -> IdentityError.PLATFORM_IDENTITY_ROLE_SCOPE_NOT_ALLOWED);
    }

    private AdminOutcome<RoleView> store(Role role, RoleChange change) {
        Role stored = roles.insert(role, change);
        events.publish(stored);
        cache.evictAll();
        return new AdminOutcome.Done<>(RoleView.of(stored));
    }

    private static RoleDraft draft(SaveRoleCommand command, Role role) {
        return new RoleDraft(command.code(), command.name(), command.enabled(), command.accessScope(), command.permissionCodes(), RoleDraft.unitIdsOf(role.customOrgUnits()));
    }
}
