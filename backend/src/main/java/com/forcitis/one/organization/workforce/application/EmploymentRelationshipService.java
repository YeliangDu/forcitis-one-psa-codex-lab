package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipView;
import com.forcitis.one.organization.workforce.api.EmploymentRelationships;
import com.forcitis.one.organization.workforce.domain.EmploymentRelationshipQueries;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class EmploymentRelationshipService implements EmploymentRelationships {
    private final EmploymentRelationshipQueries queries;

    EmploymentRelationshipService(EmploymentRelationshipQueries queries) {
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmploymentRelationshipView> on(EmployeeId employee, LocalDate date) {
        return queries.inServiceOn(employee, date);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmploymentRelationshipView> get(EmploymentRelationshipId id) {
        return queries.latest(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmploymentRelationshipView> employerOn(EmploymentRelationshipId id, LocalDate date) {
        return queries.versionOn(id, date);
    }
}
