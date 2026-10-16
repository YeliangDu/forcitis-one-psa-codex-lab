package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.integration.domain.ExternalConnections;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The integration layer's own test base for the to-do inbox of the old system: one PostgreSQL 18 container, a stand-in for the old view with a read-only
 * account that can select that view and nothing else, and the application with its other modules' ports replaced by doubles. It does not borrow the
 * approval module's test base (P3b final review Low-6).
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "forcitis.platform.approval.deadline.enabled=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@Import(LegacyInboxSupport.Doubles.class)
abstract class LegacyInboxSupport {

    static final AtomicLong IDS = new AtomicLong(100_000);

    static final PostgreSQLContainer POSTGRES = TestPostgres.container()
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    /** Throw-away credentials for the stand-in read-only account, generated per run. */
    static final String LEGACY_PASSWORD = UUID.randomUUID().toString();

    static {
        POSTGRES.start();
        prepareLegacyStandIn();
    }

    private static void prepareLegacyStandIn() {
        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA legacy_fixture");
            statement.execute("CREATE TABLE legacy_fixture.todo_rows (assignee_user_id bigint, task_id text, process_instance_id text,"
                    + " task_def_key text, task_name text, process_title text, created_at timestamp, result integer)");
            statement.execute("CREATE VIEW legacy_fixture.v_psa_one_legacy_todo AS SELECT assignee_user_id, task_id, process_instance_id,"
                    + " task_def_key, task_name, process_title, created_at FROM legacy_fixture.todo_rows WHERE result = 1");
            statement.execute("CREATE ROLE legacy_ro LOGIN PASSWORD '" + LEGACY_PASSWORD + "'");
            statement.execute("GRANT USAGE ON SCHEMA legacy_fixture TO legacy_ro");
            statement.execute("GRANT SELECT ON legacy_fixture.v_psa_one_legacy_todo TO legacy_ro");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("cannot prepare the legacy stand-in", e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("forcitis.platform.integration.legacy.inbox.url", POSTGRES::getJdbcUrl);
        registry.add("forcitis.platform.integration.legacy.inbox.username", () -> "legacy_ro");
        registry.add("forcitis.platform.integration.legacy.inbox.password", () -> LEGACY_PASSWORD);
        registry.add("forcitis.platform.integration.legacy.inbox.view-name", () -> "legacy_fixture.v_psa_one_legacy_todo");
        registry.add("forcitis.platform.integration.legacy.inbox.detail-url-template",
                () -> "https://legacy.example.test/bpm/detail?instance={processInstanceId}&task={taskId}");
    }

    @MockitoBean
    protected WecomGateway wecom;

    @MockitoBean
    protected AuditLog audit;

    @MockitoBean
    protected DocumentNumbers numbers;

    @MockitoBean
    protected com.forcitis.one.organization.workforce.api.EmployeeLegalEntities employeeLegalEntities;

    protected static long newId() {
        return IDS.incrementAndGet();
    }

    /** Hands out a plain stand-in connection and remembers what was asked for. */
    public static class RecordingExternalConnections implements ExternalConnections {

        public final List<String> opened = new CopyOnWriteArrayList<>();

        @Override
        public DataSource open(String poolName, String jdbcUrl, String username, String password, ExternalCallKind kind) {
            opened.add(poolName + " " + kind);
            return new DriverManagerDataSource(jdbcUrl, username, password);
        }
    }

    @TestConfiguration
    public static class Doubles {

        /** The connection factory checks every external login against the SQL Server read-only baseline (ADR-0037 item 4); the stand-in is PostgreSQL, so this test opens it through this double of the port. */
        @Bean
        @Primary
        RecordingExternalConnections recordingExternalConnections() {
            return new RecordingExternalConnections();
        }
    }
}
