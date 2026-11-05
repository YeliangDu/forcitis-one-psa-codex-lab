package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipView;
import com.forcitis.one.organization.workforce.domain.EmploymentRelationshipQueries;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEmploymentRelationshipQueries implements EmploymentRelationshipQueries {
    private static final String VIEW = """
            select r.employment_relationship_id, r.employer_legal_entity_id,
                   (r.employer_source_kind = 'DECLARED' or r.employer_confirmed_at is not null) as confirmed,
                   v.employment_status, v.employment_phase_kind, v.is_on_unpaid_leave
              from organization_workforce.employment_relationships r
              join organization_workforce.employment_relationship_versions v on v.employment_relationship_id = r.employment_relationship_id and v.superseded_at is null
            """;
    private final JdbcClient jdbc;

    JdbcEmploymentRelationshipQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EmploymentRelationshipView> inServiceOn(EmployeeId employee, LocalDate day) {
        return jdbc.sql(VIEW + """
                 where r.employee_id = :employee and v.employment_status = 'EMPLOYED'
                   and v.effective_from_date <= :day and (v.effective_to_date is null or :day < v.effective_to_date)
                 order by r.created_at, r.employment_relationship_id
                """).param("employee", employee.value()).param("day", day).query((rs, i) -> view(rs)).list();
    }

    @Override
    public Optional<EmploymentRelationshipView> latest(EmploymentRelationshipId id) {
        return jdbc.sql(VIEW + " where r.employment_relationship_id = :id order by v.effective_from_date desc, v.revision_number desc limit 1")
                .param("id", id.value()).query((rs, i) -> view(rs)).optional();
    }

    @Override
    public Optional<EmploymentRelationshipView> versionOn(EmploymentRelationshipId id, LocalDate day) {
        return jdbc.sql(VIEW + """
                 where r.employment_relationship_id = :id and v.effective_from_date <= :day and (v.effective_to_date is null or :day < v.effective_to_date)
                """).param("id", id.value()).param("day", day).query((rs, i) -> view(rs)).optional();
    }

    private static EmploymentRelationshipView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        boolean employed = "EMPLOYED".equals(rs.getString("employment_status"));
        String phase = rs.getString("employment_phase_kind");
        return new EmploymentRelationshipView(new EmploymentRelationshipId(rs.getObject("employment_relationship_id", UUID.class)),
                new LegalEntityId(rs.getObject("employer_legal_entity_id", UUID.class)), rs.getBoolean("confirmed"),
                employed && phase != null ? EmploymentPhaseKind.valueOf(phase) : null, employed && rs.getBoolean("is_on_unpaid_leave"));
    }
}
