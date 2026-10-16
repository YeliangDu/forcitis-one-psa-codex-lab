package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleDraft;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.RoleViolation;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A role that carries a sensitive permission is created only after a second person confirmed it. */
@Service
public class CreateRole {

    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final AccessCache cache;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final AuthorizationGate gate;
    private final IdGenerator ids;

    CreateRole(RoleRepository roles, PrivilegeCeiling ceiling, AccessCache cache, DomainEvents events, CurrentUser currentUser,
            AuthorizationGate gate, IdGenerator ids) {
        this.ids = ids;
        this.roles = roles;
        this.ceiling = ceiling;
        this.cache = cache;
        this.events = events;
        this.currentUser = currentUser;
        this.gate = gate;
    }

    @Transactional
    public AdminOutcome<RoleView> execute(SaveRoleCommand command) {
        try {
            Role role = build(command);
            if (!SensitivePermissions.within(role.permissionCodes()).isEmpty()) {
                if (!ceiling.mayRequest(role.permissionCodes(), false)) {
                    return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
                }
                return new AdminOutcome.Awaiting<>(gate.openCreateRole(currentUser.userId(), draft(command)).value().toString());
            }
            if (!ceiling.mayGrant(role.permissionCodes(), false)) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            return store(role);
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }

    @Transactional
    AdminOutcome<RoleView> applyConfirmed(SaveRoleCommand command) {
        try {
            return store(build(command));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }

    private Role build(SaveRoleCommand command) {
        if (command.code() == null || command.code().isBlank()) {
            throw new RoleRuleException(RoleViolation.INVALID_CODE);
        }
        Role role = Role.newCustom(new RoleId(ids.next()), new RoleCode(command.code().strip()), command.name(), command.permissionCodes());
        if (command.accessScope() != null) {
            role.revise(role.name(), command.accessScope(), role.permissionCodes());
        }
        role.declareEnabled(command.enabled());
        return role;
    }

    private AdminOutcome<RoleView> store(Role role) {
        Role stored = roles.insert(role);
        events.publish(stored);
        cache.evictAll();
        return new AdminOutcome.Done<>(RoleView.of(stored));
    }

    private static RoleDraft draft(SaveRoleCommand command) {
        return new RoleDraft(command.code(), command.name(), command.enabled(), command.accessScope(), command.permissionCodes());
    }
}
