package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRole {

    private final RoleRepository roles;
    private final AccessCache cache;
    private final DomainEvents events;

    DeleteRole(RoleRepository roles, AccessCache cache, DomainEvents events) {
        this.roles = roles;
        this.cache = cache;
        this.events = events;
    }

    @Transactional
    public void execute(long roleId, ExpectedVersion expected) {
        Role role = roleId <= 0 ? null : roles.findById(new RoleId(roleId)).orElse(null);
        if (role == null) {
            throw IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND.refusal("no role " + roleId);
        }
        expected.check(role.version());
        role.delete();
        roles.delete(role);
        events.publish(role);
        cache.evictAll();
    }
}
