package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.ListLegalEntities;
import com.forcitis.one.platform.integration.infrastructure.transport.MigrateOnlyLauncher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The deployment as INFRA_RUNBOOK describes it, with the real roles: one_db_roles_schemas.sql runs in an empty database, the migrations run with the
 * migration account (profile migrate,dev), the engine tables are installed once, and the application then runs as the runtime account with nothing but
 * the grants the script gave it. A missing grant shows up as a failure here instead of on the first deployment.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "forcitis.platform.approval.process-administrator-required=false",
        "spring.datasource.hikari.connection-timeout=5000"})
@ActiveProfiles("dev")
class RoleSeparatedDeploymentIT {
    static final String DATABASE = "forcitis_one_dev";
    static final String MIGRATOR = "one_dev_migrator";
    static final String RUNTIME = "one_dev_app";
    static final String[] JOBS_OFF = {"forcitis.platform.integration.archive-store.enabled=false", "forcitis.platform.approval.process-archive.enabled=false",
            "forcitis.platform.audit.archive.enabled=false", "forcitis.cost.timesheet.consumed-events.enabled=false",
            "forcitis.platform.integration.outbound-purge.enabled=false",
            "forcitis.platform.integration.event-publication.jobs-enabled=false", "forcitis.platform.scheduling.jobs-enabled=false"};

    static final PostgreSQLContainer POSTGRES = TestPostgres.container().withDatabaseName(DATABASE)
            .withCommand("sh", "-c", "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -out /var/lib/postgresql/server.crt -keyout /var/lib/postgresql/server.key"
                    + " && chmod 600 /var/lib/postgresql/server.key && chown postgres:postgres /var/lib/postgresql/server.key /var/lib/postgresql/server.crt"
                    + " && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/var/lib/postgresql/server.crt -c ssl_key_file=/var/lib/postgresql/server.key");
    static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();
    static final String RUNTIME_PASSWORD = UUID.randomUUID().toString();

    static {
        POSTGRES.start();
        try {
            prepare();
        } catch (Exception e) {
            throw new IllegalStateException("the deployment could not be prepared", e);
        }
    }

    private static String url() {
        return POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "sslmode=require";
    }

    private static Connection superuser() throws SQLException {
        return DriverManager.getConnection(url(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void prepare() throws Exception {
        String script = Files.readString(Path.of("../docs/ops/one-db-roles-schemas.sql"));
        int afterMigrationAt = script.indexOf("-- §4b");
        try (Connection c = superuser()) {
            c.createStatement().execute(script.substring(0, afterMigrationAt));
            c.createStatement().execute("GRANT CREATE ON DATABASE " + DATABASE + " TO one_dev_owner");
            c.createStatement().execute("ALTER ROLE " + MIGRATOR + " PASSWORD '" + MIGRATOR_PASSWORD + "'");
            c.createStatement().execute("ALTER ROLE " + RUNTIME + " PASSWORD '" + RUNTIME_PASSWORD + "'");
        }
        migrateWithTheMigrationAccount();
        installTheProcessEngine();
        String afterMigration = script.substring(afterMigrationAt, script.indexOf("-- §5 撤销"));
        try (Connection c = superuser()) {
            c.createStatement().execute(afterMigration);
        }
    }

    private static void migrateWithTheMigrationAccount() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"),
                MigrateOnlyLauncher.class.getName(), "--spring.profiles.active=migrate,dev", "--spring.datasource.url=" + url(),
                "--spring.datasource.username=" + MIGRATOR, "--spring.datasource.password=" + MIGRATOR_PASSWORD));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(180, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }

    private static void installTheProcessEngine() {
        List<String> args = new ArrayList<>(List.of("--spring.profiles.active=dev", "--spring.datasource.url=" + url(), "--spring.datasource.username=" + MIGRATOR,
                "--spring.datasource.password=" + MIGRATOR_PASSWORD, "--flowable.database-schema-update=true", "--flowable.async-executor-activate=false",
                "--forcitis.platform.approval.process-administrator-required=false", "--spring.main.banner-mode=off", "--server.port=0", "--server.address=127.0.0.1"));
        List.of(JOBS_OFF).forEach(p -> args.add("--" + p));
        try (var engineInstaller = new SpringApplicationBuilder(OneApplication.class).web(WebApplicationType.SERVLET).run(args.toArray(String[]::new))) {
            assertThat(engineInstaller.isRunning()).isTrue();
        }
    }

    @DynamicPropertySource
    static void runtimeAccount(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", RoleSeparatedDeploymentIT::url);
        registry.add("spring.datasource.username", () -> RUNTIME);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("flowable.database-schema-update", () -> "false");
        for (String property : JOBS_OFF) {
            String[] parts = property.split("=");
            registry.add(parts[0], () -> parts[1]);
        }
    }

    @Autowired
    CreateLegalEntity createLegalEntity;

    @Autowired
    ListLegalEntities listLegalEntities;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    com.forcitis.one.platform.audit.api.AuditPartitions auditPartitions;

    @Autowired
    com.forcitis.one.organization.workforce.api.EmployeeLabels employeeLabels;

    @Test
    @DisplayName("the runtime account alone is enough: a write through the application (entity, versions, event registry, audit by listener) and a read both work")
    void smokeAsTheRuntimeAccount() throws Exception {
        try (var acting = WorkContext.enter(UUID.randomUUID(), null, "test.organization.workforce.smoke")) {
            createLegalEntity.execute("SMK", "Smoke legal entity");
        }

        assertThat(listLegalEntities.execute(com.forcitis.one.kernel.PageRequest.of(null, null, "legal-entities")).items()).extracting(view -> view.code()).contains("SMK");
        await("the audit listener wrote its entry").atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> jdbc.sql("select count(*) from platform_audit.audit_entries where action = 'organization.workforce.LegalEntityRegistered'").query(Long.class).single() > 0);
        assertThat(jdbc.sql("select current_user").query(String.class).single()).isEqualTo(RUNTIME);
        assertThat(jdbc.sql("select count(*) from platform_approval.approval_requests").query(Long.class).single()).as("the approval schema is readable").isZero();
        assertThat(jdbc.sql("select count(*) from platform_approval.act_ru_task").query(Long.class).single()).as("so are the engine tables").isZero();
    }

    @Test
    @DisplayName("NITS-1 项1/项5：运行账号能读到分区是否存在（to_regclass 经 schema USAGE）与员工是否存在，缺的月份与员工答否而不是报错")
    void theRuntimeAccountCanAskWhetherAPartitionAndAnEmployeeExist() {
        java.time.YearMonth now = java.time.YearMonth.now(java.time.ZoneOffset.UTC);

        assertThat(jdbc.sql("select current_user").query(String.class).single()).isEqualTo(RUNTIME);
        assertThat(auditPartitions.exists(now)).as("the month the job pre-created").isTrue();
        assertThat(auditPartitions.exists(now.plusYears(30))).isFalse();
        com.forcitis.one.kernel.EmployeeId unknown = new com.forcitis.one.kernel.EmployeeId(UUID.randomUUID());
        assertThat(employeeLabels.labelsOf(java.util.Set.of(unknown)).containsKey(unknown)).isFalse();
    }

    @Test
    @DisplayName("the runtime account cannot change the structure, rewrite the history or touch the audit trail")
    void theRuntimeAccountHasOnlyWhatItNeeds() {
        assertThatThrownBy(() -> jdbc.sql("create table platform_approval.not_allowed (id bigint)").update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("delete from organization_workforce.legal_entity_versions").update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("truncate table organization_workforce.org_unit_versions").update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("delete from platform_audit.audit_entries").update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("update platform_audit.audit_entries set detail = 'x'").update()).rootCause().hasMessageContaining("permission denied");
        for (String table : List.of("platform_identity.role_versions", "platform_identity.account_role_grants", "platform_identity.permission_adjustments")) {
            assertThatThrownBy(() -> jdbc.sql("delete from " + table).update()).as(table).rootCause().hasMessageContaining("permission denied");
            assertThatThrownBy(() -> jdbc.sql("truncate table " + table).update()).as(table).rootCause().hasMessageContaining("permission denied");
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the pre-creation job runs as the runtime account and has created the months ahead; that account cannot run the drop function, detach or drop a partition")
    void theRuntimeAccountPreCreatesButCannotRemovePartitions() {
        java.time.YearMonth now = java.time.YearMonth.now(java.time.ZoneOffset.UTC);
        java.time.YearMonth ahead = now.plusMonths(3);
        java.time.YearMonth beyond = now.plusMonths(12);
        String beyondStart = beyond.atDay(1).toString();
        String beyondName = "platform_audit.audit_entries_p" + beyond.toString().replace("-", "");
        assertThat(jdbc.sql("select to_regclass(:name) is not null").param("name", "platform_audit.audit_entries_p" + ahead.toString().replace("-", "")).query(Boolean.class).single())
                .as("created by AuditPartitionJob at start-up, with the grant of the roles script").isTrue();
        assertThat(jdbc.sql("select platform_audit.create_audit_entry_partitions(date '2026-10-01', date '2026-10-01')").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select platform_audit.create_audit_entry_partitions(date '" + beyondStart + "', date '" + beyondStart + "')").query(Long.class).single()).as("a month beyond the months the job pre-creates is created by the runtime account").isEqualTo(1L);
        assertThat(jdbc.sql("select pg_get_userbyid(relowner) from pg_class where oid = '" + beyondName + "'::regclass").query(String.class).single())
                .as("the new partition belongs to the owner, not to the runtime account").isEqualTo("one_dev_owner");
        for (String privilege : List.of("TRUNCATE", "UPDATE", "DELETE")) {
            assertThat(jdbc.sql("select has_table_privilege('one_dev_app', '" + beyondName + "', '" + privilege + "')").query(Boolean.class).single()).as(privilege).isFalse();
        }

        assertThatThrownBy(() -> jdbc.sql("select platform_audit.drop_archived_audit_entry_partition(date '2026-10-01')").query(Long.class).single()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("alter table platform_audit.audit_entries detach partition platform_audit.audit_entries_p202610").update()).rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> jdbc.sql("drop table platform_audit.audit_entries_p202610").update()).rootCause().hasMessageContaining("must be owner");
        assertThatThrownBy(() -> jdbc.sql("create table platform_audit.audit_entries_p209901 partition of platform_audit.audit_entries for values from ('2099-01-01') to ('2099-02-01')").update())
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the maintenance role may run the drop function and nothing else of the two: it reaches the function's own checks, but cannot pre-create partitions")
    void theMaintenanceRoleRunsOnlyTheDropFunction() throws Exception {
        try (Connection c = superuser(); var statement = c.createStatement()) {
            statement.execute("SET ROLE one_dev_audit_maintainer");
            assertThatThrownBy(() -> statement.execute("select platform_audit.drop_archived_audit_entry_partition(date '2026-10-01')")).hasMessageContaining("nothing has been archived yet");
            assertThatThrownBy(() -> statement.execute("select platform_audit.create_audit_entry_partitions(date '2026-10-01', date '2026-10-01')")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> statement.execute("drop table platform_audit.audit_entries_p202610")).hasMessageContaining("must be owner");
        }
    }
}
