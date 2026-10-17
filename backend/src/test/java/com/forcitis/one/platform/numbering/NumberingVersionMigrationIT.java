package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DisplayName("ADR-0027 migration plan 2 (baseline form): every seeded rule waits for confirmation, its template is version 1")
class NumberingVersionMigrationIT {

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    @Test
    void seededRulesWaitForConfirmationWithTheirTemplateAsVersionOne() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        JdbcClient jdbc = JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        assertThat(jdbc.sql("""
                select version_number || '|' || template_text || '|' || reset_policy || '|' || start_value || '|' || status || '|' || coalesce(author_id::text, 'none')
                  from platform_numbering.numbering_rule_versions where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type = 'QUOTE')
                """).query(String.class).list()).containsExactly("1|{TYPE}-{LE}-{yyyyMM}-{seq:4}|MONTHLY|1|PENDING_CONFIRMATION|none");
        assertThat(jdbc.sql("select effective_from_date::text from platform_numbering.numbering_rule_versions where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type = 'QUOTE')").query(String.class).single())
                .as("the seeded template has no author's day to wait for").isEqualTo("2000-01-01");
        assertThat(jdbc.sql("""
                select count(*) from information_schema.columns where table_schema = 'platform_numbering' and table_name = 'numbering_rules'
                   and column_name in ('pattern', 'reset_policy', 'start_value', 'confirmation_status', 'is_active')
                """).query(Long.class).single()).as("the retired columns are gone").isZero();
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_rule_versions where status <> 'PENDING_CONFIRMATION'").query(Long.class).single())
                .as("nothing stays confirmed").isZero();
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_rule_versions").query(Long.class).single())
                .isEqualTo(jdbc.sql("select count(*) from platform_numbering.numbering_rules").query(Long.class).single());
    }
}
