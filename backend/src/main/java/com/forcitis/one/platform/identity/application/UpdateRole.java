package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import java.util.Optional;
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
    public AdminOutcome<RoleView> execute(long roleId, SaveRoleCommand command) {
        if (roleId <= 0) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
        }
        Optional<Role> existing = roles.findById(new RoleId(roleId));
        if (existing.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
        }
        try {
            Role role = existing.get();
            AccessScopeKind scope = command.accessScope() != null ? command.accessScope() : role.accessScope();
            role.revise(command.name(), scope, command.permissionCodes());
            role.declareEnabled(command.enabled());
            if (!ceiling.mayGrant(role.permissionCodes(), role.administrative())) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            roles.update(role);
            events.publish(role);
            cache.evictAll();
            return new AdminOutcome.Done<>(RoleView.of(role));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }
}
