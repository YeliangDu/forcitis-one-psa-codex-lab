package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class RoleProcessAdministrator implements ProcessAdministrator {

    private final RoleDirectory roles;
    private final RoleCode role;

    RoleProcessAdministrator(RoleDirectory roles, AssignmentProperties properties) {
        this.roles = roles;
        this.role = new RoleCode(properties.processAdministratorRole());
    }

    @Override
    public Set<UserId> holders() {
        return roles.findRoleHolders(role);
    }
}
