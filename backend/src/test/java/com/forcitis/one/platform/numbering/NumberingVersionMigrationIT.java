package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DisplayName("ADR-0027 migration plan 2: every rule that was confirmed goes back to waiting for confirmation, its template kept as version 1")
class NumberingVersionMigrationIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18").withInitScript("init-schemas.sql");

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    @Test
    void confirmedRulesAreReopenedAndTheirTemplatesKept() {
        flyway("20261014220000").migrate();
        JdbcClient jdbc = JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.sql("""
                update platform_numbering.numbering_rules
                   set pattern = '{TYPE}-{yyyy}-{seq:6}', reset_policy = 'YEARLY', start_value = 7, confirmation_status = 'CONFIRMED'
                 where document_type = 'QUOTE'
                """).update();

        flyway("latest").migrate();

        assertThat(jdbc.sql("""
                select version_number || '|' || template_text || '|' || reset_policy || '|' || start_value || '|' || status || '|' || coalesce(author_id::text, 'none')
                  from platform_numbering.numbering_rule_versions where document_type = 'QUOTE'
                """).query(String.class).list()).containsExactly("1|{TYPE}-{yyyy}-{seq:6}|YEARLY|7|PENDING_CONFIRMATION|none");
        assertThat(jdbc.sql("select effective_from_date::text from platform_numbering.numbering_rule_versions where document_type = 'QUOTE'").query(String.class).single())
                .as("the seeded template has no author's day to wait for").isEqualTo("2000-01-01");
        assertThat(jdbc.sql("""
                select count(*) from information_schema.columns where table_schema = 'platform_numbering' and table_name = 'numbering_rules'
                   and column_name in ('pattern', 'reset_policy', 'start_value', 'confirmation_status') and is_nullable = 'YES'
                """).query(Long.class).single()).as("append-only: the old columns stay, no longer required").isEqualTo(4);
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_rule_versions where status <> 'PENDING_CONFIRMATION'").query(Long.class).single())
                .as("nothing stays confirmed").isZero();
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_rule_versions").query(Long.class).single())
                .isEqualTo(jdbc.sql("select count(*) from platform_numbering.numbering_rules").query(Long.class).single());
    }
}
