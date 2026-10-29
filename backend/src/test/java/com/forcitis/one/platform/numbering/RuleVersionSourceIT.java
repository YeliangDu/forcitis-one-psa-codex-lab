package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** SEC-1021-COO-01: only a migration may write a template version without an author; the application's own account cannot, so four eyes cannot be skipped by inserting an authorless version. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
class RuleVersionSourceIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @MockitoBean
    com.forcitis.one.organization.workforce.api.LegalEntityDirectory legalEntityDirectory;

    static final TestDatabase postgres = TestPostgres.database(RuleVersionSourceIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String INSERT = "insert into platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text,"
            + " reset_policy, start_value, effective_from_date, author_id, authored_at, status, source_kind, confirmed_by, confirmed_at)"
            + " select uuidv7(), numbering_rule_id, %d, '{TYPE}-{seq:4}', 'NEVER', 1, DATE '2027-01-01', %s, now(), '%s', '%s', %s, %s"
            + " from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = '%s')";
    static final String SOMEONE = "'01900000-0000-7000-8000-000000000a01'::uuid";
    static final String ANOTHER = "'01900000-0000-7000-8000-000000000a02'::uuid";

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DataSource dataSource;

    private static int counter;
    private int next = 100;
    private String type;

    @BeforeEach
    void aFreshRule() {
        type = "SEC_TYPE_" + (++counter);
        jdbc.sql("insert into platform_numbering.document_types (document_type_id, code, owner_bc_code, is_finance_confirmation_required, effective_from_date)"
                + " values (uuidv7(), '" + type + "', 'cost.timesheet', false, DATE '2026-01-01')").update();
        jdbc.sql("insert into platform_numbering.numbering_rules (numbering_rule_id, document_type_id, version, event_count) select uuidv7(), document_type_id, 1, 0"
                + " from platform_numbering.document_types where code = '" + type + "'").update();
    }

    private String insert(String author, String status, String source, String confirmer) {
        return INSERT.formatted(next++, author, status, source, confirmer, confirmer.equals("null") ? "null" : "now()", type);
    }

    private void ownerInserts(String author, String status, String source, String confirmer) {
        jdbc.sql(insert(author, status, source, confirmer)).update();
    }

    private void asApplicationRole(String sql) throws SQLException {
        jdbc.sql("do $$ begin if not exists (select 1 from pg_roles where rolname = 'sec_app') then create role sec_app nologin; end if; end $$").update();
        jdbc.sql("grant usage on schema platform_numbering to sec_app").update();
        jdbc.sql("grant select, insert, update on platform_numbering.numbering_rule_versions, platform_numbering.numbering_rules, platform_numbering.document_types to sec_app").update();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("set role sec_app");
            try {
                statement.execute(sql);
            } finally {
                statement.execute("reset role");
            }
        }
    }

    @Test
    @DisplayName("the application's account cannot insert an authorless version, confirmed or not")
    void applicationAccountCannotWriteAuthorlessVersions() {
        for (String source : new String[] {"SEEDED", "MIGRATION"}) {
            assertThatThrownBy(() -> asApplicationRole(insert("null", "CONFIRMED", source, SOMEONE))).hasMessageContaining("can only be written by a migration");
            assertThatThrownBy(() -> asApplicationRole(insert("null", "PENDING_CONFIRMATION", source, "null"))).hasMessageContaining("can only be written by a migration");
        }
    }

    @Test
    @DisplayName("an authored version needs an author, and an authorless row cannot claim to be authored")
    void authorGoesWithTheSource() {
        assertThatThrownBy(() -> ownerInserts("null", "PENDING_CONFIRMATION", "AUTHORED", "null")).hasMessageContaining("ck_numbering_rule_versions_author_by_source");
        assertThatThrownBy(() -> ownerInserts(SOMEONE, "PENDING_CONFIRMATION", "SEEDED", "null")).hasMessageContaining("ck_numbering_rule_versions_author_by_source");
        assertThatThrownBy(() -> ownerInserts(SOMEONE, "PENDING_CONFIRMATION", "BOGUS", "null")).hasMessageContaining("ck_numbering_rule_versions_source_kind");
    }

    @Test
    @DisplayName("the author of an authored version cannot confirm it, another person can")
    void confirmerIsNotTheAuthor() {
        assertThatThrownBy(() -> ownerInserts(SOMEONE, "CONFIRMED", "AUTHORED", SOMEONE)).hasMessageContaining("ck_numbering_rule_versions_confirmer_is_not_author");
        assertThatCode(() -> ownerInserts(SOMEONE, "CONFIRMED", "AUTHORED", ANOTHER)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the source of a saved version cannot be rewritten")
    void sourceKindIsImmutable() {
        ownerInserts("null", "PENDING_CONFIRMATION", "SEEDED", "null");

        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set source_kind = 'AUTHORED' where source_kind = 'SEEDED' and version_number >= 100 and numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules r join platform_numbering.document_types t using (document_type_id) where t.code = '" + type + "')").update())
                .hasMessageContaining("immutable");
    }
}
