package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditTrail;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleId;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes a custom role together with its permission and account links (RULE-345); system roles cannot be deleted. */
@Service
public class DeleteRole {

    private final RoleRepository roles;
    private final AccessCache cache;
    private final CurrentUser currentUser;
    private final AuditTrail audit;

    DeleteRole(RoleRepository roles, AccessCache cache, CurrentUser currentUser, AuditTrail audit) {
        this.roles = roles;
        this.cache = cache;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<Void> execute(long roleId) {
        Optional<Role> existing = roleId <= 0 ? Optional.empty() : roles.findById(new RoleId(roleId));
        if (existing.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
        }
        try {
            existing.get().requireModifiable();
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
        roles.delete(existing.get().roleId());
        audit.record(new AuditEntry("platform.identity.role.deleted", currentUser.userId(), null, "role:" + roleId,
                "code=" + existing.get().code().value()));
        cache.evictAll();
        return new AdminOutcome.Done<>(null);
    }
}
