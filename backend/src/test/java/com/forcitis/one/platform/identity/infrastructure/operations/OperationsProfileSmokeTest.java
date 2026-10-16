package com.forcitis.one.platform.identity.infrastructure.operations;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.OneApplication;
import com.forcitis.one.TestPostgres;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DisplayName("OperationsProfile: the identity-operations profile starts a context, runs one command and ends without jobs (B1-M12)")
class OperationsProfileSmokeTest {
    static final String OPERATOR_ID = "01900000-0000-7000-8000-00000000a001";
    static final String WITNESS_ID = "01900000-0000-7000-8000-00000000a002";


    static final PostgreSQLContainer POSTGRES = TestPostgres.container()
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        POSTGRES.start();
    }

    record Finished(int exitCode, boolean anyJobBean, boolean anyScheduledTask, boolean schedulingProcessor, int webServerPort) {
    }

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private Finished run(String... commandLine) {
        AtomicInteger exit = new AtomicInteger(-1);
        SpringApplication application = new SpringApplication(OneApplication.class);
        application.setAdditionalProfiles("identity-operations");
        application.addInitializers(context -> context.getBeanFactory().registerSingleton("processExit", (ProcessExit) exit::set));
        String[] infrastructure = {
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(), "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(), "--spring.datasource.hikari.connection-timeout=2000",
                "--flowable.database-schema-update=true", "--spring.flyway.create-schemas=true", "--forcitis.cost.timesheet.consumed-events.enabled=false",
                "--forcitis.platform.approval.process-archive.enabled=false", "--forcitis.organization.workforce.sync-purge.enabled=false",
                "--forcitis.platform.integration.outbound-purge.enabled=false", "--forcitis.platform.integration.archive-store.enabled=false",
                "--forcitis.platform.audit.archive.enabled=false", "--forcitis.platform.approval.process-administrator-required=false", "--forcitis.platform.identity.sso.enabled=false",
                "--forcitis.platform.identity.emergency.totp-encryption-key=" + Base64.getEncoder().encodeToString(random(32)),
                "--FORCITIS_TICKET_KEY_KM=" + HexFormat.of().formatHex(random(32))};
        String[] all = java.util.stream.Stream.concat(java.util.Arrays.stream(infrastructure), java.util.Arrays.stream(commandLine))
                .toArray(String[]::new);
        try (ConfigurableApplicationContext context = application.run(all)) {
            boolean anyJobBean = java.util.stream.Stream.of("emergencyAdminCheckJob", "credentialPurgeJob", "orgSnapshotSyncJob",
                    "shortNameRefreshJob", "eventPublicationJobs").anyMatch(context::containsBean);
            boolean anyScheduledTask = context.getBeanProvider(ScheduledAnnotationBeanPostProcessor.class).stream()
                    .flatMap(processor -> processor.getScheduledTasks().stream()).anyMatch(task -> task.toString().matches(".*(EmergencyAdminCheckJob|CredentialPurgeJob|OrgSnapshotSyncJob|ShortNameRefreshJob|EventPublicationJobs).*"));
            int port = context instanceof org.springframework.boot.web.server.context.WebServerApplicationContext web ? web.getWebServer().getPort() : -2;
            boolean schedulingProcessor = context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class).length > 0;
            return new Finished(exit.get(), anyJobBean, anyScheduledTask, schedulingProcessor, port);
        }
    }

    @Test
    @DisplayName("a command line without an operation ends with exit code 2, and the context has no job of identity, workforce or the event registry")
    void noOperationEndsWithTheUsageCode() {
        Finished finished = run("--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID);

        assertThat(finished.exitCode()).isEqualTo(2);
        assertThat(finished.webServerPort()).as("B1-R3 the web part reports port -1; Tomcat still listens on an ephemeral port until OPS-PORT").isEqualTo(-1);
        assertThat(finished.anyJobBean()).isFalse();
        assertThat(finished.anyScheduledTask()).isFalse();
        assertThat(finished.schedulingProcessor()).as("the Moments auto-configuration stays excluded, so nothing switches scheduling on").isFalse();
    }

    @Test
    @DisplayName("a granting command ends with exit code 0, its event stays in the registry for the running application, and nothing was scheduled")
    void aCommandEndsCleanlyAndItsEventIsNotLost() {
        run("--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID);
        JdbcClient jdbc = JdbcClient.create(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.sql("insert into platform_identity.user_accounts (user_id, employee_id) values (" + com.forcitis.one.fixture.doubles.TestAccounts.sql(7) + "::uuid, " + TestOrgIds.sqlEmployee(7) + "::uuid) on conflict do nothing").update();

        Finished finished = run("--operation=grant-role", "--employee-id=" + TestAccounts.employee(7).value(), "--role-code=business_admin", "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID);

        assertThat(finished.exitCode()).isZero();
        assertThat(finished.anyScheduledTask()).isFalse();
        assertThat(jdbc.sql("select count(*) from platform_identity.account_roles ar join platform_identity.roles r on r.role_id = ar.role_id where ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(7) + ") and r.code = 'business_admin'")
                .query(Long.class).single()).isEqualTo(1);
        long waiting = jdbc.sql("select count(*) from platform_integration.event_publication where event_type like '%AccountRolesReplaced'").query(Long.class).single();
        long delivered = jdbc.sql("select count(*) from platform_integration.event_publication_archive where event_type like '%AccountRolesReplaced'").query(Long.class).single();
        assertThat(waiting + delivered).as("published, and either delivered or still waiting for redelivery").isEqualTo(1);
    }
}
