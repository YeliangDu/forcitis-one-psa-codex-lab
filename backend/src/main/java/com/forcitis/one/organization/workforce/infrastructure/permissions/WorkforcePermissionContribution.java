package com.forcitis.one.organization.workforce.infrastructure.permissions;

import com.forcitis.one.kernel.PermissionContribution;
import com.forcitis.one.kernel.PermissionDeclaration;
import com.forcitis.one.organization.workforce.api.WorkforcePermissions;
import java.util.List;
import org.springframework.stereotype.Component;

/** Same immutable data source for Spring collection and the plain production scan. */
@Component
public class WorkforcePermissionContribution implements PermissionContribution {
    @Override
    public String owner() { return "organization.workforce"; }

    @Override
    public List<PermissionDeclaration> permissions() { return WorkforcePermissions.DECLARATIONS; }
}
