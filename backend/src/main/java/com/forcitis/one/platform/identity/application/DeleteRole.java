package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import java.util.Optional;
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
    public AdminOutcome<Void> execute(long roleId) {
        Optional<Role> existing = roleId <= 0 ? Optional.empty() : roles.findById(new RoleId(roleId));
        if (existing.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
        }
        try {
            existing.get().delete();
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
        roles.delete(existing.get());
        events.publish(existing.get());
        cache.evictAll();
        return new AdminOutcome.Done<>(null);
    }
}
