package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditTrail;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleId;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.RoleStatus;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the definition of a custom role: name, status, scope and the whole permission set (RULE-346). System roles are
 * frozen (RULE-345). Permissions are resolved on every request, so the change is live as soon as the cache is evicted.
 */
@Service
public class UpdateRole {

    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final AccessCache cache;
    private final CurrentUser currentUser;
    private final AuditTrail audit;

    UpdateRole(RoleRepository roles, PrivilegeCeiling ceiling, AccessCache cache, CurrentUser currentUser, AuditTrail audit) {
        this.roles = roles;
        this.ceiling = ceiling;
        this.cache = cache;
        this.currentUser = currentUser;
        this.audit = audit;
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
            AccessScopeKind scope = command.accessScope() != null ? command.accessScope() : existing.get().accessScope();
            Role revised = existing.get().revise(command.name(), command.enabled() ? RoleStatus.ENABLED : RoleStatus.DISABLED,
                    scope, command.permissionCodes());
            if (!ceiling.mayGrant(revised.permissionCodes(), revised.administrative())) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            roles.update(revised);
            audit.record(new AuditEntry("platform.identity.role.updated", currentUser.userId(), null, "role:" + roleId,
                    "code=" + revised.code().value() + " enabled=" + command.enabled() + " scope=" + scope));
            cache.evictAll();
            return new AdminOutcome.Done<>(RoleView.of(revised));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }
}
