package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.domain.NumberingPattern;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import com.forcitis.one.platform.numbering.domain.TemplateVersion;
import com.forcitis.one.platform.numbering.domain.VersionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNumberingRuleRepository implements NumberingRuleRepository {

    private final JdbcClient jdbc;

    JdbcNumberingRuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<NumberingRule> findActive(DocumentType type) {
        return jdbc.sql("select document_type, version, event_count from platform_numbering.numbering_rules where document_type = :type and is_active")
                .param("type", type.name())
                .query((rs, n) -> new Head(DocumentType.valueOf(rs.getString("document_type")), rs.getLong("version"), rs.getLong("event_count")))
                .optional().map(head -> rule(head, versionsOf(type.name())));
    }

    @Override
    public List<NumberingRule> findAllActive() {
        List<Head> heads = jdbc.sql("select document_type, version, event_count from platform_numbering.numbering_rules where is_active order by document_type")
                .query((rs, n) -> new Head(DocumentType.valueOf(rs.getString("document_type")), rs.getLong("version"), rs.getLong("event_count")))
                .list();
        return heads.stream().map(head -> rule(head, versionsOf(head.type().name()))).toList();
    }

    private record Head(DocumentType type, long version, long eventCount) {
    }

    private static NumberingRule rule(Head head, List<TemplateVersion> versions) {
        return NumberingRule.restore(head.type(), versions, head.version(), head.eventCount());
    }

    private List<TemplateVersion> versionsOf(String type) {
        return jdbc.sql("""
                        select version_number, template_text, reset_policy, start_value, effective_from_date, author_id, authored_at, status, confirmed_by, confirmed_at,
                               finance_confirmed_by, finance_confirmed_at
                          from platform_numbering.numbering_rule_versions
                         where document_type = :type
                         order by version_number
                        """)
                .param("type", type)
                .query((rs, n) -> new TemplateVersion(rs.getInt("version_number"),
                        new NumberingPattern(rs.getString("template_text"), ResetPolicy.valueOf(rs.getString("reset_policy"))), rs.getLong("start_value"),
                        rs.getObject("effective_from_date", LocalDate.class), user(rs, "author_id"), rs.getObject("authored_at", OffsetDateTime.class).toInstant(), VersionStatus.valueOf(rs.getString("status")),
                        user(rs, "confirmed_by"), instant(rs, "confirmed_at"), user(rs, "finance_confirmed_by"), instant(rs, "finance_confirmed_at")))
                .list();
    }

    private static UserId user(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : new UserId(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @Override
    public long maxLastValue(DocumentType type) {
        return jdbc.sql("""
                        select coalesce(max(last_value), 0)
                          from platform_numbering.numbering_counters
                         where document_type = :type
                        """)
                .param("type", type.name())
                .query(Long.class)
                .single();
    }

    @Override
    public boolean hasCounterFor(long legalEntityId) {
        return jdbc.sql("select exists (select 1 from platform_numbering.numbering_counters where legal_entity_id = :le)")
                .param("le", legalEntityId).query(Boolean.class).single();
    }

    @Override
    public void save(NumberingRule rule) {
        int updated = jdbc.sql("""
                        update platform_numbering.numbering_rules
                           set updated_at = now(), version = :next, event_count = :eventSequence
                         where document_type = :type and version = :version
                        """)
                .param("type", rule.documentType().name())
                .param("next", rule.versionAfterSave()).param("eventSequence", rule.eventSequenceAfterSave()).param("version", rule.version())
                .update();
        if (updated != 1) {
            throw concurrentUpdate(rule);
        }
        for (TemplateVersion changed : rule.changedVersions()) {
            if (changed.number() > rule.loadedVersionCount()) {
                insert(rule.documentType(), changed);
            } else {
                update(rule, changed);
            }
        }
    }

    private void insert(DocumentType type, TemplateVersion saved) {
        jdbc.sql("""
                        insert into platform_numbering.numbering_rule_versions (document_type, version_number, template_text, reset_policy, start_value,
                                                                                effective_from_date, author_id, authored_at, status)
                        values (:type, :number, :template, :reset, :start, :effectiveFrom, :author, :authoredAt, :status)
                        """)
                .param("type", type.name()).param("number", saved.number()).param("template", saved.pattern().value())
                .param("reset", saved.pattern().resetPolicy().name()).param("start", saved.startValue()).param("effectiveFrom", saved.effectiveFrom())
                .param("author", saved.author() == null ? null : saved.author().value(), Types.BIGINT)
                .param("authoredAt", OffsetDateTime.ofInstant(saved.authoredAt(), ZoneOffset.UTC)).param("status", saved.status().name()).update();
    }

    private void update(NumberingRule rule, TemplateVersion changed) {
        VersionStatus before = rule.loadedStatusOf(changed.number());
        int updated = jdbc.sql("""
                        update platform_numbering.numbering_rule_versions
                           set status = :status, confirmed_by = :confirmedBy, confirmed_at = :confirmedAt,
                               finance_confirmed_by = :financeBy, finance_confirmed_at = :financeAt, updated_at = now()
                         where document_type = :type and version_number = :number and status = :before
                        """)
                .param("status", changed.status().name()).param("confirmedBy", changed.confirmedBy() == null ? null : changed.confirmedBy().value(), Types.BIGINT)
                .param("confirmedAt", changed.confirmedAt() == null ? null : OffsetDateTime.ofInstant(changed.confirmedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("financeBy", changed.financeConfirmedBy() == null ? null : changed.financeConfirmedBy().value(), Types.BIGINT)
                .param("financeAt", changed.financeConfirmedAt() == null ? null : OffsetDateTime.ofInstant(changed.financeConfirmedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("type", rule.documentType().name()).param("number", changed.number()).param("before", before.name()).update();
        if (updated != 1) {
            throw concurrentUpdate(rule);
        }
    }

    private static DomainException concurrentUpdate(NumberingRule rule) {
        return new DomainException(NumberingError.PLATFORM_NUMBERING_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                "the numbering rule of " + rule.documentType() + " was changed by someone else");
    }

    @Override
    public long reserveNext(DocumentType type, long counterScope, String periodKey, long startValue) {
        return jdbc.sql("""
                        insert into platform_numbering.numbering_counters (document_type, legal_entity_id, period_key, last_value)
                        values (:type, :le, :period, :start)
                        on conflict (document_type, legal_entity_id, period_key) do update
                           set last_value = platform_numbering.numbering_counters.last_value + 1, updated_at = now()
                        returning last_value
                        """)
                .param("type", type.name())
                .param("le", counterScope)
                .param("period", periodKey)
                .param("start", startValue)
                .query(Long.class)
                .single();
    }
}
