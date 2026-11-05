package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.PrimaryPost;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** The primary-post history is readable exactly when the employee detail is: same data scope, same denied audit; empty means not found. */
@Service
public class GetPrimaryPostHistory {
    private final GetEmployee employee;
    private final EmployeeRosterQueries queries;

    GetPrimaryPostHistory(GetEmployee employee, EmployeeRosterQueries queries) {
        this.employee = employee;
        this.queries = queries;
    }

    public Optional<List<PrimaryPost>> execute(EmployeeId id) {
        return employee.execute(id).map(visible -> queries.primaryPostHistory(id));
    }
}
