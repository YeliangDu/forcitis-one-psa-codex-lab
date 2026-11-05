package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.domain.SeparationInitiator;
import com.forcitis.one.organization.workforce.domain.SeparationRequest;
import com.forcitis.one.organization.workforce.domain.SeparationRequestId;
import com.forcitis.one.organization.workforce.domain.SeparationRequestRepository;
import com.forcitis.one.organization.workforce.domain.SeparationRequestSource;
import com.forcitis.one.organization.workforce.domain.SeparationRequestStatus;
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
class JdbcSeparationRequestRepository implements SeparationRequestRepository {
    private static final String SELECT = """
            select separation_request_id, employment_relationship_id, employee_id, initiator_kind, separation_basis_key, request_source_kind, requested_by, decided_by,
                   requested_on_date, approved_on_date, last_working_date, handover_completed_on_date, agreement_file_id, protected_circumstances_checked_on_date,
                   union_notified_on_date, union_opinion_note, union_notice_result_note, note, personnel_command_id, status, version
              from organization_workforce.separation_requests
            """;

    private final JdbcClient jdbc;

    JdbcSeparationRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SeparationRequest> find(SeparationRequestId id) {
        return jdbc.sql(SELECT + " where separation_request_id = :id").param("id", id.value()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<SeparationRequest> ofRelationship(EmploymentRelationshipId relationship) {
        return jdbc.sql(SELECT + " where employment_relationship_id = :relationship order by requested_on_date, separation_request_id")
                .param("relationship", relationship.value()).query((rs, n) -> row(rs)).list();
    }

    @Override
    public void insert(SeparationRequest r) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.sql("""
                insert into organization_workforce.separation_requests (separation_request_id, employment_relationship_id, employee_id, initiator_kind, separation_basis_key,
                    request_source_kind, requested_by, decided_by, requested_on_date, approved_on_date, last_working_date, handover_completed_on_date, agreement_file_id,
                    protected_circumstances_checked_on_date, union_notified_on_date, union_opinion_note, union_notice_result_note, note, personnel_command_id, status, created_at, updated_at)
                values (:id, :relationship, :employee, :initiator, :basis, :source, :requestedBy, :decidedBy, :requestedOn, :approvedOn, :lastWorking, :handover, :agreement,
                    :protectedChecked, :unionNotified, :unionOpinion, :unionResult, :note, :command, :status, :now, :now)""")
                .param("id", r.id().value()).param("relationship", r.relationshipId().value()).param("employee", r.employeeId().value()).param("initiator", r.initiator().name())
                .param("basis", r.basisKey()).param("source", r.source().name()).param("requestedBy", r.requestedBy().value())
                .param("decidedBy", r.decidedBy() == null ? null : r.decidedBy().value()).param("requestedOn", r.requestedOn()).param("approvedOn", r.approvedOn())
                .param("lastWorking", r.lastWorkingDate()).param("handover", r.handoverCompletedOn()).param("agreement", r.agreementFileId())
                .param("protectedChecked", r.protectedCircumstancesCheckedOn()).param("unionNotified", r.unionNotifiedOn()).param("unionOpinion", r.unionOpinionNote())
                .param("unionResult", r.unionNoticeResultNote()).param("note", r.note()).param("command", r.personnelCommandId() == null ? null : r.personnelCommandId().value())
                .param("status", r.status().name()).param("now", now).update();
    }

    private static SeparationRequest row(ResultSet rs) throws SQLException {
        return new SeparationRequest(new SeparationRequestId(rs.getObject("separation_request_id", UUID.class)),
                new EmploymentRelationshipId(rs.getObject("employment_relationship_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                SeparationInitiator.valueOf(rs.getString("initiator_kind")), rs.getString("separation_basis_key"), SeparationRequestSource.valueOf(rs.getString("request_source_kind")),
                new UserId(rs.getObject("requested_by", UUID.class)), user(rs.getObject("decided_by", UUID.class)), rs.getObject("requested_on_date", LocalDate.class),
                rs.getObject("approved_on_date", LocalDate.class), rs.getObject("last_working_date", LocalDate.class), rs.getObject("handover_completed_on_date", LocalDate.class),
                rs.getObject("agreement_file_id", UUID.class), rs.getObject("protected_circumstances_checked_on_date", LocalDate.class),
                rs.getObject("union_notified_on_date", LocalDate.class), rs.getString("union_opinion_note"), rs.getString("union_notice_result_note"), rs.getString("note"),
                command(rs.getObject("personnel_command_id", UUID.class)), SeparationRequestStatus.valueOf(rs.getString("status")), rs.getLong("version"));
    }

    private static @Nullable UserId user(@Nullable UUID id) {
        return id == null ? null : new UserId(id);
    }

    private static @Nullable PersonnelCommandId command(@Nullable UUID id) {
        return id == null ? null : new PersonnelCommandId(id);
    }
}
