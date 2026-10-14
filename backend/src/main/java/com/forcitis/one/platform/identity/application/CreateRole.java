package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.RoleStatus;
import com.forcitis.one.platform.identity.domain.RoleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateRole {

    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final AccessCache cache;
    private final CurrentUser currentUser;
    private final AuditLog audit;

    CreateRole(RoleRepository roles, PrivilegeCeiling ceiling, AccessCache cache, CurrentUser currentUser, AuditLog audit) {
        this.roles = roles;
        this.ceiling = ceiling;
        this.cache = cache;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<RoleView> execute(SaveRoleCommand command) {
        try {
            if (command.code() == null || command.code().isBlank()) {
                throw new RoleRuleException(RoleViolation.INVALID_CODE);
            }
            Role role = Role.newCustom(new RoleCode(command.code().strip()), command.name(), command.permissionCodes());
            if (command.accessScope() != null || !command.enabled()) {
                role = role.revise(role.name(), command.enabled() ? role.status() : RoleStatus.DISABLED,
                        command.accessScope() != null ? command.accessScope() : role.accessScope(), role.permissionCodes());
            }
            if (!ceiling.mayGrant(role.permissionCodes(), false)) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            Role stored = roles.insert(role);
            audit.record(new AuditEntry("platform.identity.role.created", currentUser.userId(), null,
                    "role:" + stored.roleId().value(), "code=" + stored.code().value()));
            cache.evictAll();
            return new AdminOutcome.Done<>(RoleView.of(stored));
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
    }
}
