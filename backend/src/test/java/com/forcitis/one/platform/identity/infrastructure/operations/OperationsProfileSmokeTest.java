package com.forcitis.one.platform.identity.infrastructure.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.OneApplication;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.platform.audit.AuditPartitionsTestSupport;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.EmployeeLabel;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
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
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval")
            .withCommand("sh", "-c", "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -out /var/lib/postgresql/server.crt -keyout /var/lib/postgresql/server.key"
                    + " && chmod 600 /var/lib/postgresql/server.key && chown postgres:postgres /var/lib/postgresql/server.key /var/lib/postgresql/server.crt"
                    + " && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/var/lib/postgresql/server.crt -c ssl_key_file=/var/lib/postgresql/server.key");

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

    /** The organisation snapshot belongs to another module; the real service answers, except that the two synthetic attestors are known to exist. */
    private static OrgDirectory withAttestors(OrgDirectory real) {
        OrgDirectory directory = Mockito.mock(OrgDirectory.class, AdditionalAnswers.delegatesTo(real));
        for (String attestor : List.of(OPERATOR_ID, WITNESS_ID)) {
            EmployeeId id = EmployeeId.parse(attestor);
            Mockito.doReturn(Optional.of(new EmployeeProfile(id, "Synthetic attestor", TestOrgIds.orgUnit(10), null, EmploymentStatus.EMPLOYED, false)))
                    .when(directory).findEmployee(id);
        }
        // The granted person has an account row but no employee in the real snapshot; the grant judges employment (#1333), so the directory must say employed.
        Mockito.doReturn(com.forcitis.one.organization.workforce.api.EmploymentVerdict.EMPLOYED).when(directory).employmentOf(TestAccounts.employee(7));
        return directory;
    }

    private static EmployeeLabels withAttestors(EmployeeLabels real) {
        Set<EmployeeId> attestors = Set.of(EmployeeId.parse(OPERATOR_ID), EmployeeId.parse(WITNESS_ID));
        return ids -> {
            Map<EmployeeId, EmployeeLabel> known = new HashMap<>(real.labelsOf(ids));
            ids.stream().filter(attestors::contains).forEach(id -> known.putIfAbsent(id, new EmployeeLabel("Synthetic attestor", "SYN")));
            return known;
        };
    }

    /** The profile does not create partitions, so a command that writes audit needs the running month to exist; the function exists only once an earlier start has migrated. */
    private static void ensureCurrentMonthPartition() {
        DriverManagerDataSource source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (java.sql.Connection connection = source.getConnection()) {
            AuditPartitionsTestSupport.ensureCurrentMonthIfMigrated(connection);
        } catch (java.sql.SQLException failed) {
            throw new IllegalStateException(failed);
        }
    }

    private Finished run(String... commandLine) {
        ensureCurrentMonthPartition();
        AtomicInteger exit = new AtomicInteger(-1);
        SpringApplication application = new SpringApplication(OneApplication.class);
        application.setAdditionalProfiles("dev", "identity-operations");
        application.addInitializers(context -> context.getBeanFactory().registerSingleton("processExit", (ProcessExit) exit::set));
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beans -> beans.addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("employeeLabelsService".equals(beanName) && bean instanceof EmployeeLabels real) {
                    return withAttestors(real);
                }
                return "orgDirectoryService".equals(beanName) && bean instanceof OrgDirectory real ? withAttestors(real) : bean;
            }
        })));
        String[] infrastructure = {
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl() + "&sslmode=require", "--spring.flyway.enabled=true", "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(), "--spring.datasource.hikari.connection-timeout=2000",
                "--flowable.database-schema-update=true", "--spring.flyway.create-schemas=true", "--forcitis.cost.timesheet.consumed-events.enabled=false",
                "--forcitis.platform.approval.process-archive.enabled=false",
                "--forcitis.platform.integration.outbound-purge.enabled=false", "--forcitis.platform.integration.archive-store.enabled=false",
                "--forcitis.platform.audit.archive.enabled=false", "--forcitis.platform.approval.process-administrator-required=false",
                "--forcitis.platform.identity.emergency.totp-encryption-key=" + Base64.getEncoder().encodeToString(random(32)),
                "--FORCITIS_TICKET_KEY_KM=" + HexFormat.of().formatHex(random(32))};
        String[] all = Stream.concat(Arrays.stream(infrastructure), Arrays.stream(commandLine))
                .toArray(String[]::new);
        try (ConfigurableApplicationContext context = application.run(all)) {
            boolean anyJobBean = Stream.of("emergencyAdminCheckJob", "credentialPurgeJob", "orgSnapshotSyncJob",
                    "shortNameRefreshJob", "eventPublicationJobs").anyMatch(context::containsBean);
            boolean anyScheduledTask = context.getBeanProvider(ScheduledAnnotationBeanPostProcessor.class).stream()
                    .flatMap(processor -> processor.getScheduledTasks().stream()).anyMatch(task -> task.toString().matches(".*(EmergencyAdminCheckJob|CredentialPurgeJob|OrgSnapshotSyncJob|ShortNameRefreshJob|EventPublicationJobs).*"));
            int port = context instanceof WebServerApplicationContext web ? web.getWebServer().getPort() : -2;
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
        jdbc.sql("insert into platform_identity.user_accounts (user_id, employee_id) values (" + TestAccounts.sql(7) + "::uuid, " + TestOrgIds.sqlEmployee(7) + "::uuid) on conflict do nothing").update();

        Finished finished = run("--operation=grant-role", "--employee-id=" + TestAccounts.employee(7).value(), "--role-code=business_admin", "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID);

        assertThat(finished.exitCode()).isZero();
        assertThat(finished.anyScheduledTask()).isFalse();
        assertThat(jdbc.sql("select count(*) from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id where ar.effective_to_at is null and ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(7) + ") and r.code = 'business_admin'")
                .query(Long.class).single()).isEqualTo(1);
        long waiting = jdbc.sql("select count(*) from platform_integration.event_publication where event_type like '%AccountRolesReplaced'").query(Long.class).single();
        long delivered = jdbc.sql("select count(*) from platform_integration.event_publication_archive where event_type like '%AccountRolesReplaced'").query(Long.class).single();
        assertThat(waiting + delivered).as("published, and either delivered or still waiting for redelivery").isEqualTo(1);
    }
}
