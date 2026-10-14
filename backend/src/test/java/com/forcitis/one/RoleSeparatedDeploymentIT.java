package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            "forcitis.organization.workforce.sync-purge.enabled=false", "forcitis.platform.integration.outbound-purge.enabled=false",
            "forcitis.platform.integration.event-publication.jobs-enabled=false", "forcitis.platform.scheduling.jobs-enabled=false",
            "forcitis.platform.identity.sso.enabled=false"};

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18").withDatabaseName(DATABASE)
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
        String script = Files.readString(Path.of("../docs/modernization/phase1a/infra/one_db_roles_schemas.sql"));
        try (Connection c = superuser()) {
            c.createStatement().execute(script);
            c.createStatement().execute("GRANT CREATE ON DATABASE " + DATABASE + " TO one_dev_owner");
            c.createStatement().execute("ALTER ROLE " + MIGRATOR + " PASSWORD '" + MIGRATOR_PASSWORD + "'");
            c.createStatement().execute("ALTER ROLE " + RUNTIME + " PASSWORD '" + RUNTIME_PASSWORD + "'");
        }
        migrateWithTheMigrationAccount();
        installTheProcessEngine();
        String afterMigration = script.substring(script.indexOf("-- §4b"), script.indexOf("-- §5 撤销"));
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
                "--forcitis.platform.approval.process-administrator-required=false", "--spring.main.banner-mode=off", "--server.port=0"));
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

    @Test
    @DisplayName("the runtime account alone is enough: a write through the application (entity, versions, event registry, audit by listener) and a read both work")
    void smokeAsTheRuntimeAccount() throws Exception {
        try (var acting = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.smoke")) {
            createLegalEntity.execute("SMK", "Smoke legal entity", null);
        }

        assertThat(listLegalEntities.execute(com.forcitis.one.kernel.PageRequest.of(null, null, "legal-entities")).items()).extracting(view -> view.code()).contains("SMK");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (jdbc.sql("select count(*) from platform_audit.audit_entries where action = 'organization.workforce.LegalEntityRegistered'").query(Long.class).single() == 0) {
            assertThat(System.nanoTime()).as("the audit listener wrote its entry").isLessThan(deadline);
            Thread.sleep(100);
        }
        assertThat(jdbc.sql("select current_user").query(String.class).single()).isEqualTo(RUNTIME);
        assertThat(jdbc.sql("select count(*) from platform_approval.approval_requests").query(Long.class).single()).as("the approval schema is readable").isZero();
        assertThat(jdbc.sql("select count(*) from platform_approval.act_ru_task").query(Long.class).single()).as("so are the engine tables").isZero();
    }

    @Test
    @DisplayName("the runtime account cannot change the structure, rewrite the history or touch the audit trail")
    void theRuntimeAccountHasOnlyWhatItNeeds() {
        assertThatThrownBy(() -> jdbc.sql("create table platform_approval.not_allowed (id bigint)").update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("delete from organization_workforce.legal_entity_versions").update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("truncate table organization_workforce.org_unit_versions").update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("delete from platform_audit.audit_entries").update()).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.sql("update platform_audit.audit_entries set detail = 'x'").update()).rootCause().hasMessageContaining("permission denied");
    }
}
