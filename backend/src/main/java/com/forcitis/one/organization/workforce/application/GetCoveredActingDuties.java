package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Reads duties naming the covered person; visibility, denied audit and not-found are checked against that path employee. */
@Service
public class GetCoveredActingDuties {
    private final GetEmployee employee;
    private final EmployeeRosterQueries queries;
    private final ActingDutyViews views;

    GetCoveredActingDuties(GetEmployee employee, EmployeeRosterQueries queries, ActingDutyViews views) {
        this.employee = employee;
        this.queries = queries;
        this.views = views;
    }

    public Optional<List<ActingDutyView>> execute(EmployeeId id) {
        return employee.execute(id).map(visible -> views.of(queries.coveredActingDuties(id)));
    }
}
