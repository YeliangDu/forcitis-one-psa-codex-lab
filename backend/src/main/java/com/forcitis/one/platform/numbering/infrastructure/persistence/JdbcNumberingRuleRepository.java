package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.domain.NumberingPattern;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
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
        return jdbc.sql("""
                        select document_type, pattern, reset_policy, start_value
                          from platform_numbering.numbering_rules
                         where document_type = :type and is_active
                        """)
                .param("type", type.name())
                .query((rs, n) -> new NumberingRule(DocumentType.valueOf(rs.getString("document_type")),
                        new NumberingPattern(rs.getString("pattern"), ResetPolicy.valueOf(rs.getString("reset_policy"))),
                        rs.getLong("start_value")))
                .optional();
    }

    @Override
    public List<NumberingRule> findAllActive() {
        return jdbc.sql("""
                        select document_type, pattern, reset_policy, start_value
                          from platform_numbering.numbering_rules
                         where is_active
                        """)
                .query((rs, n) -> new NumberingRule(DocumentType.valueOf(rs.getString("document_type")),
                        new NumberingPattern(rs.getString("pattern"), ResetPolicy.valueOf(rs.getString("reset_policy"))),
                        rs.getLong("start_value")))
                .list();
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
    public void save(NumberingRule rule) {
        jdbc.sql("""
                        insert into platform_numbering.numbering_rules (document_type, pattern, reset_policy, start_value)
                        values (:type, :pattern, :reset, :start)
                        on conflict (document_type) do update
                           set pattern = excluded.pattern, reset_policy = excluded.reset_policy,
                               start_value = excluded.start_value, updated_at = now(),
                               version = platform_numbering.numbering_rules.version + 1
                        """)
                .param("type", rule.documentType().name())
                .param("pattern", rule.pattern().value())
                .param("reset", rule.pattern().resetPolicy().name())
                .param("start", rule.startValue())
                .update();
    }

    @Override
    public long reserveNext(DocumentType type, LegalEntityId legalEntity, String periodKey, long startValue) {
        return jdbc.sql("""
                        insert into platform_numbering.numbering_counters (document_type, legal_entity_id, period_key, last_value)
                        values (:type, :le, :period, :start)
                        on conflict (document_type, legal_entity_id, period_key) do update
                           set last_value = platform_numbering.numbering_counters.last_value + 1, updated_at = now()
                        returning last_value
                        """)
                .param("type", type.name())
                .param("le", legalEntity.value())
                .param("period", periodKey)
                .param("start", startValue)
                .query(Long.class)
                .single();
    }
}
