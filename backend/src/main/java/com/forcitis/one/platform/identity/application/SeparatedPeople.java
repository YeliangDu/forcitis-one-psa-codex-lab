package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import java.util.ArrayList;
import java.util.List;

/** Who left according to a snapshot change: those it lists as separated, and changed ones the directory no longer shows as employed. */
final class SeparatedPeople {

    private SeparatedPeople() {
    }

    static List<UserId> in(OrgSnapshotChanged event, OrgDirectory directory) {
        List<UserId> separated = new ArrayList<>(event.separatedEmployees());
        for (UserId changed : event.changedEmployees()) {
            directory.findEmployee(changed).filter(employee -> employee.employmentStatus() != EmploymentStatus.EMPLOYED)
                    .ifPresent(employee -> separated.add(changed));
        }
        return separated;
    }
}
