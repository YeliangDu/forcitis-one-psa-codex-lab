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

    /** A person is on unpaid leave when every relationship in service on the day is (ADR-0086 design 6.2). */
    @Override
    public boolean isOnLeave(EmployeeId employee, LocalDate day) {
        return jdbc.sql("""
                select 1 where exists (select 1 from organization_workforce.employment_relationship_versions
                                        where employee_id = :employee and superseded_at is null and employment_status = 'EMPLOYED'
                                          and effective_from_date <= :day and (effective_to_date is null or :day < effective_to_date))
                   and not exists (select 1 from organization_workforce.employment_relationship_versions
                                    where employee_id = :employee and superseded_at is null and employment_status = 'EMPLOYED' and not is_on_unpaid_leave
                                      and effective_from_date <= :day and (effective_to_date is null or :day < effective_to_date))
                """).param("employee", employee.value()).param("day", day).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean hasVersions(EmployeeId employee) {
        return jdbc.sql("select 1 from organization_workforce.employment_relationship_versions where employee_id = :employee limit 1")
                .param("employee", employee.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public Optional<EmploymentStatus> employmentOn(EmployeeId employee, LocalDate day) {
        return jdbc.sql("""
                select employment_status from organization_workforce.employment_relationship_versions
                 where employee_id = :employee and superseded_at is null
                   and effective_from_date <= :day and (effective_to_date is null or :day < effective_to_date)
                 order by (employment_status = 'EMPLOYED') desc limit 1
                """).param("employee", employee.value()).param("day", day).query(String.class).optional().map(EmploymentStatus::valueOf);
    }
}
