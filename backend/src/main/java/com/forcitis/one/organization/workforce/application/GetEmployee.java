package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetEmployee {
    private final EmployeeRosterQueries queries;
    private final EmployeeVisibility visibility;
    private final AuditLog audit;
    private final CurrentUser currentUser;

    GetEmployee(EmployeeRosterQueries queries, EmployeeVisibility visibility, AuditLog audit, CurrentUser currentUser) {
        this.queries = queries;
        this.visibility = visibility;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public Optional<EmployeeRosterView> execute(EmployeeId employee) {
        var row = queries.find(visibility.forDetail(), employee);
        if (row.isEmpty() && queries.exists(employee)) {
            audit.recordIndependently(new AuditEntry("organization.workforce.employee.denied", currentUser.userId(), null,
                    AuditSubject.of(AuditSubjectKind.EMPLOYEE, employee.value()), "outside the scope of the caller"));
        }
        return row.map(EmployeeRosterView::of);
    }
}
