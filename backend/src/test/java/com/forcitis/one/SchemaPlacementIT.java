package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.datasource.hikari.connection-timeout=2000", "spring.flyway.create-schemas=false"})
@Testcontainers
class SchemaPlacementIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18").withInitScript("init-dba-schemas.sql");

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("INFRA_RUNBOOK section 2: the Flyway history table is in one_migration and every engine table is in platform_approval")
    void historyAndEngineTablesLandInTheirOwnSchemas() {
        assertThat(schemasOf("flyway_schema_history")).containsExactly("one_migration");

        List<String> engine = jdbc.sql("select table_schema from information_schema.tables where table_name like 'act\\_%' group by table_schema")
                .query(String.class).list();
        assertThat(engine).containsExactly("platform_approval");
        assertThat(schemasOf("act_ru_task")).containsExactly("platform_approval");
    }

    @Test
    @DisplayName("INFRA_RUNBOOK section 2: the history schema holds nothing but the history table, and module tables stay in their own schemas")
    void moduleTablesStayInTheirSchemas() {
        List<String> inMigration = jdbc.sql("select table_name from information_schema.tables where table_schema = 'one_migration'")
                .query(String.class).list();
        assertThat(inMigration).containsExactly("flyway_schema_history");
        assertThat(schemasOf("timesheets")).containsExactly("cost_timesheet");
        assertThat(schemasOf("employees")).containsExactly("organization_workforce");
    }

    private List<String> schemasOf(String table) {
        return jdbc.sql("select table_schema from information_schema.tables where table_name = :t order by 1").param("t", table).query(String.class).list();
    }
}
