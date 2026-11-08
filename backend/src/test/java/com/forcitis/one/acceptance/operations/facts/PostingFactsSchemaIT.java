package com.forcitis.one.acceptance.operations.facts;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Lab #30 F1: own-schema metadata only, no producer rows or business API. */
class PostingFactsSchemaIT {
    static final TestDatabase postgres = TestPostgres.database(PostingFactsSchemaIT.class, TestPostgres.MIGRATED);

    @Test
    void freshMigrationCreatesOnlySignedEmptyTablesSequenceAndDeferredTriggers() throws Exception {
        try (Connection db = DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password())) {
            assertThat(strings(db, "select schema_name from information_schema.schemata where schema_name='operations_facts'"))
                    .as("missing signed operations_facts schema").containsExactly("operations_facts");
            assertThat(strings(db, "select table_name from information_schema.tables where table_schema='operations_facts' and table_type='BASE TABLE' order by table_name"))
                    .containsExactly("posting_components", "posting_fact_lines", "posting_facts");
            Map<String, List<String>> columns = Map.of(
                "posting_facts", List.of("id:uuid:NO", "post_number:int8:NO", "legal_entity_id:uuid:NO", "fact_type:text:NO",
                    "business_date:date:NO", "posted_fiscal_year_number:int4:NO", "posted_period_number:int4:NO",
                    "original_fiscal_year_number:int4:YES", "original_period_number:int4:YES", "source_context_code:text:NO",
                    "source_id:uuid:NO", "idempotency_key:text:NO", "reverses_fact_id:uuid:YES", "created_at:timestamptz:NO", "updated_at:timestamptz:NO"),
                "posting_fact_lines", List.of("id:uuid:NO", "fact_id:uuid:NO", "line_number:int4:NO", "side_kind:text:NO",
                    "component_key:text:NO", "line_amount:numeric:NO", "line_currency:text:NO", "department_id:uuid:YES", "created_at:timestamptz:NO", "updated_at:timestamptz:NO"),
                "posting_components", List.of("id:uuid:NO", "fact_type:text:NO", "component_key:text:NO", "side_hint_kind:text:YES",
                    "is_active:bool:NO", "created_at:timestamptz:NO", "updated_at:timestamptz:NO"));
            for (var entry : columns.entrySet()) {
                assertThat(strings(db, "select column_name||':'||udt_name||':'||is_nullable from information_schema.columns where table_schema='operations_facts' and table_name='"+entry.getKey()+"'"))
                        .containsExactlyInAnyOrderElementsOf(entry.getValue());
                assertThat(strings(db, "select count(*)::text from operations_facts."+entry.getKey())).containsExactly("0");
                assertThat(strings(db, "select column_name from information_schema.columns c where table_schema='operations_facts' and table_name='"+entry.getKey()+"' and col_description(('operations_facts.'||table_name)::regclass,ordinal_position) is null"))
                        .as("all signed columns commented").isEmpty();
            }
            assertThat(strings(db, "select numeric_precision||':'||numeric_scale from information_schema.columns where table_schema='operations_facts' and table_name='posting_fact_lines' and column_name='line_amount'"))
                    .containsExactly("19:2");
            assertThat(strings(db, "select sequencename from pg_sequences where schemaname='operations_facts'"))
                    .containsExactly("posting_fact_post_seq");
            assertThat(strings(db, "select column_default from information_schema.columns where table_schema='operations_facts' and table_name='posting_facts' and column_name='post_number'"))
                    .singleElement().asString().contains("nextval", "operations_facts.posting_fact_post_seq");
            assertThat(strings(db, "select indexname from pg_indexes where schemaname='operations_facts'"))
                    .contains("pk_posting_facts", "pk_posting_fact_lines", "pk_posting_components",
                        "uk_posting_facts_post_number", "uk_posting_facts_replay_key", "uk_posting_facts_reverses_fact_id",
                        "uk_posting_fact_lines_fact_id_line_number", "uk_posting_components_fact_type_component_key",
                        "ix_posting_facts_legal_entity_id_post_number", "ix_posting_facts_legal_entity_id_posted_period", "ix_posting_facts_source");
            assertThat(strings(db, "select tgname from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='operations_facts' and not t.tgisinternal"))
                    .contains("tg_posting_facts_append_only", "tg_posting_facts_truncate", "tg_posting_fact_lines_append_only",
                        "tg_posting_fact_lines_truncate", "tg_posting_fact_lines_component", "tg_posting_facts_balance", "tg_posting_fact_lines_balance");
            assertThat(strings(db, "select tgname||':'||tgdeferrable||':'||tginitdeferred from pg_trigger where tgname in ('tg_posting_facts_balance','tg_posting_fact_lines_balance')"))
                    .containsExactlyInAnyOrder("tg_posting_facts_balance:true:true", "tg_posting_fact_lines_balance:true:true");
            assertThat(strings(db, "select count(distinct tgfoid)::text from pg_trigger where tgname in ('tg_posting_facts_balance','tg_posting_fact_lines_balance')"))
                    .containsExactly("1");
            assertThat(strings(db, "select conname from pg_constraint k join pg_class a on a.oid=k.conrelid join pg_namespace na on na.oid=a.relnamespace join pg_class b on b.oid=k.confrelid join pg_namespace nb on nb.oid=b.relnamespace where na.nspname='operations_facts' and k.contype='f' and (nb.nspname<>'operations_facts' or k.confdeltype<>'r')"))
                    .as("no cross-schema FK; own FKs restrict deletion").isEmpty();
        }
    }

    static List<String> strings(Connection db, String sql) throws Exception {
        List<String> result = new ArrayList<>();
        try (var statement=db.createStatement(); var rows=statement.executeQuery(sql)) { while(rows.next()) result.add(rows.getString(1)); }
        return result;
    }
}
