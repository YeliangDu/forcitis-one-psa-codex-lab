package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permissions are resolved on every request, so a change is live once the cache is evicted. */
@Service
public class UpdateRole {

    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final AccessCache cache;
    private final DomainEvents events;

    UpdateRole(RoleRepository roles, PrivilegeCeiling ceiling, AccessCache cache, DomainEvents events) {
        this.roles = roles;
        this.ceiling = ceiling;
        this.cache = cache;
        this.events = events;
    }

    @Transactional
    public RoleView execute(long roleId, ExpectedVersion expected, SaveRoleCommand command) {
        Role role = roleId <= 0 ? null : roles.findById(new RoleId(roleId)).orElse(null);
        if (role == null) {
            throw IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND.refusal("no role " + roleId);
        }
        expected.check(role.version());
        AccessScopeKind scope = command.accessScope() != null ? command.accessScope() : role.accessScope();
        role.revise(command.name(), scope, command.permissionCodes());
        role.declareEnabled(command.enabled());
        if (!ceiling.mayGrant(role.permissionCodes(), role.administrative())) {
            throw IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED.refusal("the caller may not grant these permissions");
        }
        roles.update(role);
        events.publish(role);
        cache.evictAll();
        return RoleView.of(role);
    }
}
