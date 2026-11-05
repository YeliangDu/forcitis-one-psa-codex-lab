package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.domain.ContractKind;
import com.forcitis.one.organization.workforce.domain.ContractStatus;
import com.forcitis.one.organization.workforce.domain.EmploymentContract;
import com.forcitis.one.organization.workforce.domain.EmploymentContractId;
import com.forcitis.one.organization.workforce.domain.EmploymentContractRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEmploymentContractRepository implements EmploymentContractRepository {
    private static final String SELECT = """
            select employment_contract_id, employment_relationship_id, employee_id, contract_kind, term_start_date, term_end_date, signed_on_date, probation_months_count,
                   renews_contract_id, contract_file_id, status, submitted_by, approved_by, terminated_on_date, termination_basis_key, version
              from organization_workforce.employment_contracts
            """;

    private final JdbcClient jdbc;

    JdbcEmploymentContractRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EmploymentContract> find(EmploymentContractId id) {
        return jdbc.sql(SELECT + " where employment_contract_id = :id").param("id", id.value()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<EmploymentContract> ofRelationship(EmploymentRelationshipId relationship) {
        return jdbc.sql(SELECT + " where employment_relationship_id = :relationship order by term_start_date, employment_contract_id")
                .param("relationship", relationship.value()).query((rs, n) -> row(rs)).list();
    }

    @Override
    public void insert(EmploymentContract c) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.sql("""
                insert into organization_workforce.employment_contracts (employment_contract_id, employment_relationship_id, employee_id, contract_kind, term_start_date, term_end_date,
                    probation_months_count, renews_contract_id, contract_file_id, status, created_at, updated_at)
                values (:id, :relationship, :employee, :kind, :termStart, :termEnd, :probation, :renews, :file, :status, :now, :now)""")
                .param("id", c.id().value()).param("relationship", c.relationshipId().value()).param("employee", c.employeeId().value()).param("kind", c.kind().name())
                .param("termStart", c.termStart()).param("termEnd", c.termEnd()).param("probation", c.probationMonths())
                .param("renews", c.renews() == null ? null : c.renews().value()).param("file", c.contractFileId()).param("status", c.status().name()).param("now", now).update();
    }

    private static EmploymentContract row(ResultSet rs) throws SQLException {
        return new EmploymentContract(new EmploymentContractId(rs.getObject("employment_contract_id", UUID.class)),
                new EmploymentRelationshipId(rs.getObject("employment_relationship_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                ContractKind.valueOf(rs.getString("contract_kind")), rs.getObject("term_start_date", LocalDate.class), rs.getObject("term_end_date", LocalDate.class),
                rs.getObject("signed_on_date", LocalDate.class), rs.getInt("probation_months_count"), contractId(rs.getObject("renews_contract_id", UUID.class)),
                rs.getObject("contract_file_id", UUID.class), ContractStatus.valueOf(rs.getString("status")), user(rs.getObject("submitted_by", UUID.class)),
                user(rs.getObject("approved_by", UUID.class)), rs.getObject("terminated_on_date", LocalDate.class), rs.getString("termination_basis_key"), rs.getLong("version"));
    }

    private static @Nullable EmploymentContractId contractId(@Nullable UUID id) {
        return id == null ? null : new EmploymentContractId(id);
    }

    private static @Nullable UserId user(@Nullable UUID id) {
        return id == null ? null : new UserId(id);
    }
}
