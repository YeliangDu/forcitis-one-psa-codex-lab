package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Reads a stand-in's duties with the employee detail's visibility, denied audit and not-found result. */
@Service
public class GetActingDuties {
    private final GetEmployee employee;
    private final EmployeeRosterQueries queries;
    private final ActingDutyViews views;

    GetActingDuties(GetEmployee employee, EmployeeRosterQueries queries, ActingDutyViews views) {
        this.employee = employee;
        this.queries = queries;
        this.views = views;
    }

    public Optional<List<ActingDutyView>> execute(EmployeeId id) {
        return employee.execute(id).map(visible -> views.of(queries.actingDuties(id)));
    }
}
