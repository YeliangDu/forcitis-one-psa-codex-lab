package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.EmployeeHired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * A hire took effect (ADR-0068 item 1.9): the person gets an account with the default role, opened as every account entry opens it (ADR-0033 section 3
 * item 1). Repeated or concurrent deliveries find the account already open; a person who is not employed on the directory's reading gets none.
 */
@SystemActorName("platform.identity.account-provisioning")
@Service
public class HandleEmployeeHired {

    private final OrgDirectory directory;
    private final AccountProvisioning provisioning;
    private final AccessCache cache;

    HandleEmployeeHired(OrgDirectory directory, AccountProvisioning provisioning, AccessCache cache) {
        this.directory = directory;
        this.provisioning = provisioning;
        this.cache = cache;
    }

    @ApplicationModuleListener
    public void on(EmployeeHired event) {
        directory.findEmployee(event.employeeId()).flatMap(provisioning::ensureAccount).ifPresent(cache::evict);
    }
}
