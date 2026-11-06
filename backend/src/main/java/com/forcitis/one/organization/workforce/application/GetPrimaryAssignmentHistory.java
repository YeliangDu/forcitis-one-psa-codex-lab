package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** The primary-assignment history is readable exactly when the employee detail is: same data scope, same denied audit; empty means not found. */
@Service
public class GetPrimaryAssignmentHistory {
    private final GetEmployee employee;
    private final EmployeeRosterQueries queries;

    GetPrimaryAssignmentHistory(GetEmployee employee, EmployeeRosterQueries queries) {
        this.employee = employee;
        this.queries = queries;
    }

    public Optional<List<PrimaryAssignmentView>> execute(EmployeeId id) {
        return employee.execute(id).map(visible -> queries.primaryAssignmentHistory(id).stream().map(PrimaryAssignmentView::of).toList());
    }
}
