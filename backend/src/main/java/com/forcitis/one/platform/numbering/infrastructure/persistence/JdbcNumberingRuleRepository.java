package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.numbering.api.NumberingCounterId;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.NumberingRuleId;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.domain.CounterScope;
import com.forcitis.one.platform.numbering.domain.NumberingPattern;
import com.forcitis.one.platform.numbering.domain.DocumentTypeEntry;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import com.forcitis.one.platform.numbering.domain.ReservedValue;
import com.forcitis.one.platform.numbering.domain.TemplateVersion;
import com.forcitis.one.platform.numbering.domain.VersionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNumberingRuleRepository implements NumberingRuleRepository {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdGenerator ids;

    JdbcNumberingRuleRepository(JdbcClient jdbc, Clock clock, IdGenerator ids) {
        this.ids = ids;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<NumberingRule> findActive(DocumentTypeCode type) {
        return jdbc.sql(HEADS + """
                 where t.code = :type
                """)
                .param("type", type.value())
                .query(HEAD_MAPPER)
                .optional().map(head -> rule(head, versionsOf(type.value())));
    }

    @Override
    public List<NumberingRule> findAllActive() {
        List<Head> heads = jdbc.sql(HEADS + """
                 order by t.code
                """).query(HEAD_MAPPER).list();
        return heads.stream().map(head -> rule(head, versionsOf(head.type().code().value()))).toList();
    }

    private static final String HEADS = """
            select r.numbering_rule_id, r.version, r.event_count,
            """ + JdbcDocumentTypeRegistry.COLUMNS + """
              from platform_numbering.numbering_rules r join platform_numbering.document_types t on t.document_type_id = r.document_type_id
            """;

    private static final RowMapper<Head> HEAD_MAPPER = (rs, n) -> new Head(new NumberingRuleId(rs.getObject("numbering_rule_id", UUID.class)),
            JdbcDocumentTypeRegistry.MAPPER.mapRow(rs, n), rs.getLong("version"), rs.getLong("event_count"));

    private record Head(NumberingRuleId id, DocumentTypeEntry type, long version, long eventCount) {
    }

    private static NumberingRule rule(Head head, List<TemplateVersion> versions) {
        return NumberingRule.restore(head.id(), head.type(), versions, head.version(), head.eventCount());
    }

    private List<TemplateVersion> versionsOf(String type) {
        return jdbc.sql("""
                        select numbering_rule_version_id, version_number, template_text, reset_policy, start_value, effective_from_date, author_id, authored_at, status, confirmed_by, confirmed_at,
                               finance_confirmed_by, finance_confirmed_at
                          from platform_numbering.numbering_rule_versions
                         where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = :type))
                         order by version_number
                        """)
                .param("type", type)
                .query((rs, n) -> new TemplateVersion(new NumberingRuleVersionId(rs.getObject("numbering_rule_version_id", java.util.UUID.class)), rs.getInt("version_number"),
                        new NumberingPattern(rs.getString("template_text"), ResetPolicy.valueOf(rs.getString("reset_policy"))), rs.getLong("start_value"),
                        rs.getObject("effective_from_date", LocalDate.class), user(rs, "author_id"), rs.getObject("authored_at", OffsetDateTime.class).toInstant(), VersionStatus.valueOf(rs.getString("status")),
                        user(rs, "confirmed_by"), instant(rs, "confirmed_at"), user(rs, "finance_confirmed_by"), instant(rs, "finance_confirmed_at")))
                .list();
    }

    private static UserId user(ResultSet rs, String column) throws SQLException {
        java.util.UUID value = rs.getObject(column, java.util.UUID.class);
        return value == null ? null : new UserId(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @Override
    public long maxLastValue(DocumentTypeCode type) {
        return jdbc.sql("""
                        select coalesce(max(last_value), 0)
                          from platform_numbering.numbering_counters
                         where document_type_id = (select document_type_id from platform_numbering.document_types where code = :type)
                        """)
                .param("type", type.value())
                .query(Long.class)
                .single();
    }

    @Override
    public boolean hasCounterFor(LegalEntityId legalEntityId) {
        return jdbc.sql("""
                select exists (select 1 from platform_numbering.numbering_counters where legal_entity_id = :le)
                """)
                .param("le", legalEntityId.value()).query(Boolean.class).single();
    }

    @Override
    public void save(NumberingRule rule) {
        int updated = jdbc.sql("""
                        update platform_numbering.numbering_rules
                           set updated_at = :updatedAt, version = :next, event_count = :eventSequence
                         where document_type_id = (select document_type_id from platform_numbering.document_types where code = :type) and version = :version
                        """)
                .param("type", rule.documentType().value())
                .param("next", rule.versionAfterSave()).param("eventSequence", rule.eventSequenceAfterSave()).param("version", rule.version())
                .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
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

    private void insert(DocumentTypeCode type, TemplateVersion saved) {
        jdbc.sql("""
                        insert into platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text,
                                                                                reset_policy, start_value, effective_from_date, author_id, authored_at, status, source_kind)
                        select :id, numbering_rule_id, :number, :template, :reset, :start, :effectiveFrom, :author, :authoredAt, :status, 'AUTHORED'
                          from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = :type)
                        """)
                .param("id", saved.id().value()).param("type", type.value()).param("number", saved.number()).param("template", saved.pattern().value())
                .param("reset", saved.pattern().resetPolicy().name()).param("start", saved.startValue()).param("effectiveFrom", saved.effectiveFrom())
                .param("author", saved.author() == null ? null : saved.author().value(), Types.OTHER)
                .param("authoredAt", OffsetDateTime.ofInstant(saved.authoredAt(), ZoneOffset.UTC)).param("status", saved.status().name()).update();
    }

    private void update(NumberingRule rule, TemplateVersion changed) {
        VersionStatus before = rule.loadedStatusOf(changed.number());
        int updated = jdbc.sql("""
                        update platform_numbering.numbering_rule_versions
                           set status = :status, confirmed_by = :confirmedBy, confirmed_at = :confirmedAt,
                               finance_confirmed_by = :financeBy, finance_confirmed_at = :financeAt, updated_at = :updatedAt
                         where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = :type))
                           and version_number = :number and status = :before
                        """)
                .param("status", changed.status().name()).param("confirmedBy", changed.confirmedBy() == null ? null : changed.confirmedBy().value(), Types.OTHER)
                .param("confirmedAt", changed.confirmedAt() == null ? null : OffsetDateTime.ofInstant(changed.confirmedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("financeBy", changed.financeConfirmedBy() == null ? null : changed.financeConfirmedBy().value(), Types.OTHER)
                .param("financeAt", changed.financeConfirmedAt() == null ? null : OffsetDateTime.ofInstant(changed.financeConfirmedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("type", rule.documentType().value()).param("number", changed.number()).param("before", before.name()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw concurrentUpdate(rule);
        }
    }

    private static DomainException concurrentUpdate(NumberingRule rule) {
        return new DomainException(NumberingError.PLATFORM_NUMBERING_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                "the numbering rule of " + rule.documentType() + " was changed by someone else");
    }

    @Override
    public ReservedValue reserveNext(DocumentTypeCode type, CounterScope scope, String periodKey, long startValue) {
        return jdbc.sql("""
                        insert into platform_numbering.numbering_counters (numbering_counter_id, document_type_id, counter_scope_kind, legal_entity_id, period_key, last_value)
                        values (:id, (select document_type_id from platform_numbering.document_types where code = :type), :scopeKind, :le, :period, :start)
                        on conflict (document_type_id, legal_entity_id, period_key) do update
                           set last_value = platform_numbering.numbering_counters.last_value + 1, updated_at = :updatedAt
                        returning last_value, numbering_counter_id
                        """)
                .param("id", ids.next())
                .param("type", type.value())
                .param("scopeKind", scope.kind().name())
                .param("le", scope.legalEntity() == null ? null : scope.legalEntity().value(), Types.OTHER)
                .param("period", periodKey)
                .param("start", startValue)
                .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .query((rs, n) -> new ReservedValue(rs.getLong("last_value"), new NumberingCounterId(rs.getObject("numbering_counter_id", UUID.class))))
                .single();
    }
}
