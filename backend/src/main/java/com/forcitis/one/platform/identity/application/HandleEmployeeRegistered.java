package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.EmployeeRegistered;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@SystemActorName("platform.identity.account-provisioning")
@Service
public class HandleEmployeeRegistered {

    private final OrgDirectory directory;
    private final AccountProvisioning provisioning;
    private final AccessCache cache;

    HandleEmployeeRegistered(OrgDirectory directory, AccountProvisioning provisioning, AccessCache cache) {
        this.directory = directory;
        this.provisioning = provisioning;
        this.cache = cache;
    }

    @ApplicationModuleListener
    public void on(EmployeeRegistered event) {
        directory.findEmployee(event.employeeId()).flatMap(provisioning::ensureAccount).ifPresent(cache::evict);
    }
}
