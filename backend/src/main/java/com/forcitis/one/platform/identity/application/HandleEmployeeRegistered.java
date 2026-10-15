package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.EmployeeRegistered;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Service
public class HandleEmployeeRegistered {

    private final OrgDirectory directory;
    private final AccountProvisioning provisioning;
    private final AccessCache cache;
    private final AccountEmployees accountEmployees;

    HandleEmployeeRegistered(OrgDirectory directory, AccountProvisioning provisioning, AccessCache cache, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.directory = directory;
        this.provisioning = provisioning;
        this.cache = cache;
    }

    @ApplicationModuleListener
    public void on(EmployeeRegistered event) {
        directory.findEmployee(event.employeeId()).ifPresent(employee -> {
            provisioning.ensureAccount(employee);
            cache.evict(accountEmployees.accountOf(event.employeeId()));
        });
    }
}
