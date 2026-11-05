package com.forcitis.one.organization.payroll.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Money;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.payroll.api.BonusInputId;
import com.forcitis.one.organization.payroll.api.BonusInputStatus;
import com.forcitis.one.organization.payroll.domain.BonusDetermination;
import com.forcitis.one.organization.payroll.domain.BonusDeterminationRepository;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Currency;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBonusDeterminationRepository implements BonusDeterminationRepository {
    private static final String COLUMNS = """
            id, employment_relationship_id, paying_legal_entity_id, payroll_month_start_date, bonus_amount, reason_key, approval_instance_id,
            entered_by_employee_id, created_at, status, voided_by_user_id, voided_at
            """;

    private final JdbcClient jdbc;
    JdbcBonusDeterminationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** No conflict target: either unique key (the approval, or the ACTIVE business key) leaves the row unwritten, and the caller tells a replay from a duplicate. */
    @Override
    public boolean insertApproved(BonusDetermination input) {
        return jdbc.sql("""
                insert into organization_payroll.bonus_inputs (id, employment_relationship_id, paying_legal_entity_id, payroll_month_start_date, bonus_amount,
                       bonus_currency, source_kind, reason_key, approval_instance_id, entered_by_employee_id, status, created_at, updated_at)
                values (:id, :relationship, :entity, :month, :amount, 'CNY', 'MANUAL_APPROVED', :reason, :approval, :enteredBy, 'ACTIVE', :at, :at)
                on conflict do nothing
                """).param("id", input.id().value()).param("relationship", input.employmentRelationshipId().value())
                .param("entity", input.payingLegalEntityId().value()).param("month", input.payrollMonthStartDate()).param("amount", input.bonus().amount())
                .param("reason", input.reasonKey()).param("approval", input.approvalInstanceId().value()).param("enteredBy", input.enteredByEmployeeId().value())
                .param("at", at(input.createdAt())).update() == 1;
    }

    @Override
    public boolean existsForApproval(ApprovalInstanceId approval) {
        return jdbc.sql("select count(*) from organization_payroll.bonus_inputs where approval_instance_id = :approval").param("approval", approval.value())
                .query(Long.class).single() > 0;
    }

    @Override
    public Optional<BonusDetermination> find(BonusInputId id) {
        return jdbc.sql("select " + COLUMNS + " from organization_payroll.bonus_inputs where id = :id").param("id", id.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public Optional<BonusDetermination> findForUpdate(BonusInputId id) {
        return jdbc.sql("select " + COLUMNS + " from organization_payroll.bonus_inputs where id = :id for no key update").param("id", id.value())
                .query((rs, n) -> read(rs)).optional();
    }

    /** The trigger allows exactly this update of an ACTIVE row; the row was read FOR UPDATE, so no version column is needed. */
    @Override
    public void saveVoided(BonusDetermination input) {
        OffsetDateTime at = at(Objects.requireNonNull(input.voidedAt()));
        jdbc.sql("""
                update organization_payroll.bonus_inputs set status = 'VOIDED', voided_by_user_id = :by, voided_at = :at, updated_at = :at where id = :id
                """).param("by", Objects.requireNonNull(input.voidedByUserId()).value()).param("at", at).param("id", input.id().value()).update();
    }

    @Override
    public List<BonusDetermination> listForRelationship(EmploymentRelationshipId relationship, LocalDate fromMonth, LocalDate toMonth) {
        return jdbc.sql("select " + COLUMNS + """
                  from organization_payroll.bonus_inputs
                 where employment_relationship_id = :relationship and payroll_month_start_date between :fromMonth and :toMonth
                 order by payroll_month_start_date desc, id
                """).param("relationship", relationship.value()).param("fromMonth", fromMonth).param("toMonth", toMonth).query((rs, n) -> read(rs)).list();
    }

    private static OffsetDateTime at(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static BonusDetermination read(ResultSet rs) throws SQLException {
        OffsetDateTime voidedAt = rs.getObject("voided_at", OffsetDateTime.class);
        UUID voidedBy = rs.getObject("voided_by_user_id", UUID.class);
        return BonusDetermination.restore(new BonusInputId(rs.getObject("id", UUID.class)),
                new EmploymentRelationshipId(rs.getObject("employment_relationship_id", UUID.class)),
                new LegalEntityId(rs.getObject("paying_legal_entity_id", UUID.class)), rs.getObject("payroll_month_start_date", LocalDate.class),
                new Money(rs.getBigDecimal("bonus_amount"), Currency.getInstance(rs.getString("bonus_currency"))), rs.getString("reason_key"), new ApprovalInstanceId(rs.getObject("approval_instance_id", UUID.class)),
                new EmployeeId(rs.getObject("entered_by_employee_id", UUID.class)), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                BonusInputStatus.valueOf(rs.getString("status")), voidedBy == null ? null : new UserId(voidedBy), voidedAt == null ? null : voidedAt.toInstant());
    }
}
