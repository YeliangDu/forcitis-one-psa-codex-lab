package com.forcitis.one.organization.payroll.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.payroll.api.EffectivePayrollRuleVersion;
import com.forcitis.one.organization.payroll.api.JurisdictionKey;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.payroll.api.PayrollRuleKind;
import com.forcitis.one.organization.payroll.api.PayrollRuleVersionId;
import com.forcitis.one.organization.payroll.domain.PayrollRuleRowKind;
import com.forcitis.one.organization.payroll.domain.PayrollRuleRowBound;
import com.forcitis.one.organization.payroll.domain.PayrollRuleVersion;
import com.forcitis.one.organization.payroll.domain.PayrollRuleVersionRecord;
import com.forcitis.one.organization.payroll.domain.PayrollRuleVersionRepository;
import com.forcitis.one.organization.payroll.domain.PayrollRuleVersionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPayrollRuleVersionRepository implements PayrollRuleVersionRepository {
    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String COLUMNS = """
            id, rule_kind, jurisdiction_key, effective_from_date, effective_to_date, status, drafted_by_user_id, approved_by_user_id, published_at, version
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcPayrollRuleVersionRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<PayrollRuleVersion> findForUpdate(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from organization_payroll.payroll_rule_versions where id = :id for no key update").param("id", id)
                .query((rs, n) -> PayrollRuleVersion.restore(record(rs))).optional();
    }

    @Override
    public Optional<PayrollRuleVersion> findOpenEffective(PayrollRuleKind ruleKind, JurisdictionKey jurisdictionKey) {
        return jdbc.sql("select " + COLUMNS + """
                  from organization_payroll.payroll_rule_versions
                 where rule_kind = :ruleKind and jurisdiction_key = :jurisdictionKey and status = 'EFFECTIVE' and effective_to_date is null
                """).param("ruleKind", ruleKind.name()).param("jurisdictionKey", jurisdictionKey.value())
                .query((rs, n) -> PayrollRuleVersion.restore(record(rs))).optional();
    }

    @Override
    public List<PayrollRuleRowBound> rowsOf(UUID versionId) {
        return jdbc.sql("""
                select row_kind, lower_bound_amount, upper_bound_amount
                  from organization_payroll.payroll_rule_rows
                 where rule_version_id = :versionId
                 order by row_kind, row_key
                """).param("versionId", versionId)
                .query((rs, n) -> new PayrollRuleRowBound(PayrollRuleRowKind.valueOf(rs.getString("row_kind")), rs.getBigDecimal("lower_bound_amount"),
                        rs.getBigDecimal("upper_bound_amount"))).list();
    }

    /** Two publishes that read the same predecessor meet in the versioned update of it: the first commit wins. A successor that would share a day with a stored version meets the exclusion. */
    @Override
    public void save(PayrollRuleVersion version) {
        int written;
        try {
            written = jdbc.sql("""
                    update organization_payroll.payroll_rule_versions
                       set status = :status, effective_to_date = :to, approved_by_user_id = :approvedBy, published_at = :publishedAt,
                           updated_at = :now, version = :next
                     where id = :id and version = :version
                    """)
                    .param("id", version.id()).param("version", version.version()).param("next", version.versionAfterSave())
                    .param("status", version.status().name()).param("to", version.effectiveToDate())
                    .param("approvedBy", version.approvedBy() == null ? null : version.approvedBy().value(), java.sql.Types.OTHER)
                    .param("publishedAt", version.publishedAt() == null ? null : OffsetDateTime.ofInstant(version.publishedAt(), ZoneOffset.UTC),
                            java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                    .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).update();
        } catch (DataIntegrityViolationException failure) {
            if (isExclusionViolation(failure)) {
                throw new DomainException(PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_OVERLAP, ErrorKind.CONFLICT,
                        "the version would share a day with another effective version of the same category and jurisdiction");
            }
            throw failure;
        }
        if (written != 1) {
            throw new DomainException(PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_CONCURRENT_CHANGE, ErrorKind.CONFLICT,
                    "the payroll rule version was changed by another call: read it again");
        }
    }

    @Override
    public Optional<EffectivePayrollRuleVersion> effectiveOn(PayrollRuleKind ruleKind, JurisdictionKey jurisdictionKey, LocalDate date) {
        return jdbc.sql("""
                select id, rule_kind, jurisdiction_key, effective_from_date, effective_to_date
                  from organization_payroll.payroll_rule_versions
                 where rule_kind = :ruleKind and jurisdiction_key = :jurisdictionKey and status in ('EFFECTIVE', 'SUPERSEDED')
                   and effective_from_date <= :date and (effective_to_date is null or effective_to_date > :date)
                """).param("ruleKind", ruleKind.name()).param("jurisdictionKey", jurisdictionKey.value()).param("date", date)
                .query((rs, n) -> new EffectivePayrollRuleVersion(new PayrollRuleVersionId(rs.getObject("id", UUID.class)),
                        PayrollRuleKind.valueOf(rs.getString("rule_kind")), new JurisdictionKey(rs.getString("jurisdiction_key")),
                        rs.getObject("effective_from_date", LocalDate.class), rs.getObject("effective_to_date", LocalDate.class))).optional();
    }

    private static boolean isExclusionViolation(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static PayrollRuleVersionRecord record(ResultSet rs) throws SQLException {
        OffsetDateTime publishedAt = rs.getObject("published_at", OffsetDateTime.class);
        UUID approvedBy = rs.getObject("approved_by_user_id", UUID.class);
        return new PayrollRuleVersionRecord(rs.getObject("id", UUID.class), PayrollRuleKind.valueOf(rs.getString("rule_kind")),
                new JurisdictionKey(rs.getString("jurisdiction_key")), rs.getObject("effective_from_date", LocalDate.class),
                rs.getObject("effective_to_date", LocalDate.class), PayrollRuleVersionStatus.valueOf(rs.getString("status")),
                new UserId(Objects.requireNonNull(rs.getObject("drafted_by_user_id", UUID.class))), approvedBy == null ? null : new UserId(approvedBy),
                publishedAt == null ? null : publishedAt.toInstant(), rs.getLong("version"));
    }
}
