package com.forcitis.one.organization.recruitment.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.api.RequisitionStatus;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionQueries;
import com.forcitis.one.organization.recruitment.domain.RequisitionRepository;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRequisitionRepository implements RequisitionRepository, RequisitionQueries {

    private static final String SELECT = """
            select id, requisition_number, position_id, org_unit_id, legal_entity_id, requested_by_employee_id, requested_count, expected_start_date,
                   description_text, status, close_reason_kind, approval_instance_id, submitted_at, approved_at, closed_at, created_at, version
              from organization_recruitment.recruitment_requisitions
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcRequisitionRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<RecruitmentRequisition> find(RequisitionId id) {
        return jdbc.sql(SELECT + """
                 where id = :id
                """).param("id", id.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public Optional<RecruitmentRequisition> findForUpdate(RequisitionId id) {
        return jdbc.sql(SELECT + """
                 where id = :id
                   for update
                """).param("id", id.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public void lockForCreate(RequisitionId id) {
        jdbc.sql("""
                select pg_advisory_xact_lock(hashtextextended(:key, 0))
                """).param("key", "recruitment-requisition-create:" + id.value()).query().singleValue();
    }

    @Override
    public void save(RecruitmentRequisition requisition) {
        if (requisition.isNew()) {
            insert(requisition);
            return;
        }
        int updated = jdbc.sql("""
                update organization_recruitment.recruitment_requisitions
                   set status = :status, close_reason_kind = :closeReason, approved_at = :approvedAt, closed_at = :closedAt,
                       updated_at = :updatedAt, version = :next
                 where id = :id and version = :version and status = :previous
                """)
                .param("status", requisition.status().name()).param("closeReason", requisition.closeReason().map(Enum::name).orElse(null))
                .param("approvedAt", at(requisition.approvedAt().orElse(null))).param("closedAt", at(requisition.closedAt().orElse(null)))
                .param("updatedAt", at(clock.instant())).param("next", requisition.versionAfterSave())
                .param("id", requisition.requisitionId().value()).param("version", requisition.version())
                .param("previous", requisition.loadedStatus().name()).update();
        if (updated != 1) {
            throw changedByOthers(requisition);
        }
    }

    private void insert(RecruitmentRequisition requisition) {
        try {
            jdbc.sql("""
                    insert into organization_recruitment.recruitment_requisitions (id, requisition_number, position_id, org_unit_id, legal_entity_id,
                           requested_by_employee_id, requested_count, expected_start_date, description_text, status, close_reason_kind, approval_instance_id,
                           submitted_at, approved_at, closed_at, created_at, updated_at, version)
                    values (:id, :number, :position, :orgUnit, :legalEntity, :requester, :count, :startDate, :description, :status, null, :instance,
                            :submittedAt, null, null, :createdAt, :createdAt, :next)
                    """)
                    .param("id", requisition.requisitionId().value()).param("number", requisition.number().map(DocumentNumber::value).orElse(null))
                    .param("position", requisition.positionId().value()).param("orgUnit", requisition.orgUnitId().value())
                    .param("legalEntity", requisition.legalEntityId().value()).param("requester", requisition.requestedBy().value())
                    .param("count", requisition.requestedCount()).param("startDate", requisition.expectedStartDate().orElse(null))
                    .param("description", requisition.description().orElse(null)).param("status", requisition.status().name())
                    .param("instance", requisition.approvalInstanceId().map(ApprovalInstanceId::value).orElse(null))
                    .param("submittedAt", at(requisition.submittedAt().orElse(null))).param("createdAt", at(requisition.createdAt()))
                    .param("next", requisition.versionAfterSave()).update();
        } catch (DuplicateKeyException exists) {
            throw changedByOthers(requisition);
        }
    }

    @Override
    public Set<UUID> orgUnitsWithRequisitions() {
        return Set.copyOf(jdbc.sql("""
                select distinct org_unit_id from organization_recruitment.recruitment_requisitions
                """).query(UUID.class).list());
    }

    @Override
    public List<RecruitmentRequisition> list(Set<UUID> orgUnitIds, @Nullable RequisitionId after, int limit) {
        if (orgUnitIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + """
                 where org_unit_id in (:units)
                   and (cast(:after as uuid) is null
                        or (created_at, id) < (select created_at, id from organization_recruitment.recruitment_requisitions where id = cast(:after as uuid)))
                 order by created_at desc, id desc
                 limit :limit
                """).param("units", orgUnitIds).param("after", after == null ? null : after.value()).param("limit", limit).query((rs, n) -> read(rs)).list();
    }

    @Override
    public List<RecruitmentRequisition> listRequestedBy(EmployeeId requester, @Nullable RequisitionId after, int limit) {
        return jdbc.sql(SELECT + """
                 where requested_by_employee_id = :requester
                   and (cast(:after as uuid) is null
                        or (created_at, id) < (select created_at, id from organization_recruitment.recruitment_requisitions where id = cast(:after as uuid)))
                 order by created_at desc, id desc
                 limit :limit
                """).param("requester", requester.value()).param("after", after == null ? null : after.value()).param("limit", limit)
                .query((rs, n) -> read(rs)).list();
    }

    @Override
    public List<RecruitmentRequisition> listByIds(Set<UUID> ids, @Nullable RequisitionId after, int limit) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + """
                 where id in (:ids)
                   and (cast(:after as uuid) is null
                        or (created_at, id) < (select created_at, id from organization_recruitment.recruitment_requisitions where id = cast(:after as uuid)))
                 order by created_at desc, id desc
                 limit :limit
                """).param("ids", ids).param("after", after == null ? null : after.value()).param("limit", limit).query((rs, n) -> read(rs)).list();
    }

    private static @Nullable OffsetDateTime at(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static DomainException changedByOthers(RecruitmentRequisition requisition) {
        return KernelError.KERNEL_CONCURRENT_CHANGE.refusal("Requisition " + requisition.requisitionId().value() + " was changed by somebody else");
    }

    private static RecruitmentRequisition read(ResultSet rs) throws SQLException {
        return RecruitmentRequisition.restore(new RequisitionId(rs.getObject("id", UUID.class)), number(rs.getString("requisition_number")),
                new PositionId(rs.getObject("position_id", UUID.class)), new OrgUnitId(rs.getObject("org_unit_id", UUID.class)),
                new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)), new EmployeeId(rs.getObject("requested_by_employee_id", UUID.class)),
                rs.getInt("requested_count"), rs.getObject("expected_start_date", LocalDate.class), rs.getString("description_text"),
                RequisitionStatus.valueOf(rs.getString("status")), closeReason(rs.getString("close_reason_kind")),
                instance(rs.getObject("approval_instance_id", UUID.class)), instant(rs, "submitted_at"), instant(rs, "approved_at"),
                instant(rs, "closed_at"), rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getLong("version"));
    }

    private static @Nullable DocumentNumber number(@Nullable String value) {
        return value == null ? null : new DocumentNumber(value);
    }

    private static @Nullable CloseReasonKind closeReason(@Nullable String value) {
        return value == null ? null : CloseReasonKind.valueOf(value);
    }

    private static @Nullable ApprovalInstanceId instance(@Nullable UUID value) {
        return value == null ? null : new ApprovalInstanceId(value);
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
