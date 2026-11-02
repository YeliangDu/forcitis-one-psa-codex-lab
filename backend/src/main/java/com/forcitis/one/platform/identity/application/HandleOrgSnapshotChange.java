package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import java.util.Optional;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * An employed person in a snapshot gets an account if none exists (ADR-0033 section 3 entries). Separation is no longer read from the snapshot: it
 * arrives as EmployeeSeparated and is handled by {@link HandleEmployeeSeparated}.
 */
@SystemActorName("platform.identity.separation")
@Service
public class HandleOrgSnapshotChange {

    private static final String ACTOR = HandleOrgSnapshotChange.class.getAnnotation(SystemActorName.class).value();

    private final OrgDirectory directory;
    private final AccountProvisioning provisioning;
    private final AccessCache cache;

    HandleOrgSnapshotChange(OrgDirectory directory, AccountProvisioning provisioning, AccessCache cache) {
        this.directory = directory;
        this.provisioning = provisioning;
        this.cache = cache;
    }

    @ApplicationModuleListener
    public void on(OrgSnapshotChanged event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            for (EmployeeId changed : event.changedEmployees()) {
                Optional<EmployeeProfile> employee = directory.findEmployee(changed);
                if (employee.isPresent() && employee.get().employmentStatus() == EmploymentStatus.EMPLOYED) {
                    provisioning.ensureAccount(employee.get()).ifPresent(cache::evict);
                }
            }
        }
    }
}
