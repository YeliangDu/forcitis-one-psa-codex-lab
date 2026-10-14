package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.RoleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateRole {

    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final AccessCache cache;
    private final DomainEvents events;

    CreateRole(RoleRepository roles, PrivilegeCeiling ceiling, AccessCache cache, DomainEvents events) {
        this.roles = roles;
        this.ceiling = ceiling;
        this.cache = cache;
        this.events = events;
    }

    @Transactional
    public RoleView execute(SaveRoleCommand command) {
        if (command.code() == null || command.code().isBlank()) {
            throw new RoleRuleException(RoleViolation.INVALID_CODE);
        }
        Role role = Role.newCustom(new RoleCode(command.code().strip()), command.name(), command.permissionCodes());
        if (command.accessScope() != null) {
            role.revise(role.name(), command.accessScope(), role.permissionCodes());
        }
        role.declareEnabled(command.enabled());
        if (!ceiling.mayGrant(role.permissionCodes(), false)) {
            throw IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED.refusal("the caller may not grant these permissions");
        }
        Role stored = roles.insert(role);
        events.publish(stored);
        cache.evictAll();
        return RoleView.of(stored);
    }
}
