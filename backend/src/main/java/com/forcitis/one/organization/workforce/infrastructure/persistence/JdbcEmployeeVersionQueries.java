package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.domain.EmployeeVersionQueries;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEmployeeVersionQueries implements EmployeeVersionQueries {
    private final JdbcClient jdbc;

    JdbcEmployeeVersionQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isOnLeave(EmployeeId employee, LocalDate day) {
        return jdbc.sql("""
                select 1 from organization_workforce.employee_versions
                 where employee_id = :employee and superseded_at is null and is_on_unpaid_leave
                   and effective_from_date <= :day and (effective_to_date is null or :day < effective_to_date)
                """).param("employee", employee.value()).param("day", day).query(Integer.class).optional().isPresent();
    }

    @Override
    public Optional<EmploymentStatus> employmentOn(EmployeeId employee, LocalDate day) {
        return jdbc.sql("""
                select employment_status from organization_workforce.employee_versions
                 where employee_id = :employee and superseded_at is null
                   and effective_from_date <= :day and (effective_to_date is null or :day < effective_to_date)
                """).param("employee", employee.value()).param("day", day).query(String.class).optional().map(EmploymentStatus::valueOf);
    }
}
