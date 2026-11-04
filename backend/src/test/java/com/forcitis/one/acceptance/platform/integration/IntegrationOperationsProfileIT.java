package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.OneApplication;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.util.ClassUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P3b RULING-P3B-15 (review M3): smoke of the profile integration-operations as its properties file configures it. The application is
 * started a second time with that profile on the database the acceptance context migrated. The profile's runner ends the process with
 * System.exit, which would end the test JVM, so its bean definition (the one ApplicationRunner of platform.integration) is removed before
 * the context starts and the command is called directly. Besides that, the features whose configuration the test environment lacks are switched
 * off by property (the scheduled purges and consumers, the process-administrator requirement; see the arguments of application.run);
 * everything else starts as on the server.
 */
@Testcontainers
class IntegrationOperationsProfileIT extends AccountsAcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static final String PROFILE = "integration-operations";
    static final String INTEGRATION = "com.forcitis.one.platform.integration";

    static ConfigurableApplicationContext operations;
    static List<String> removedRunners = new ArrayList<>();

    record Outcome(int exit, String out, String err) {
    }

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    @BeforeEach
    void theProfileStartedOnce() {
        if (operations != null) {
            return;
        }
        SpringApplication application = new SpringApplication(OneApplication.class);
        application.setAdditionalProfiles(PROFILE);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;
            for (String name : registry.getBeanDefinitionNames()) {
                String type = registry.getBeanDefinition(name).getBeanClassName();
                if (type == null || !type.startsWith(INTEGRATION)) {
                    continue;
                }
                Class<?> runner = ClassUtils.resolveClassName(type, factory.getBeanClassLoader());
                if (ApplicationRunner.class.isAssignableFrom(runner) || CommandLineRunner.class.isAssignableFrom(runner)) {
                    registry.removeBeanDefinition(name);
                    removedRunners.add(type);
                }
            }
        }));
        operations = application.run("--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(), "--spring.datasource.hikari.connection-timeout=2000",
                "--forcitis.cost.timesheet.consumed-events.enabled=false", "--forcitis.platform.approval.process-archive.enabled=false",
                "--forcitis.organization.workforce.sync-purge.enabled=false", "--forcitis.platform.integration.outbound-purge.enabled=false",
                "--forcitis.platform.integration.archive-store.enabled=false", "--forcitis.platform.audit.archive.enabled=false",
                "--forcitis.platform.approval.process-administrator-required=false",
                "--forcitis.platform.identity.emergency.totp-encryption-key=" + Base64.getEncoder().encodeToString(random(32)),
                "--FORCITIS_TICKET_KEY_KM=" + HexFormat.of().formatHex(random(32)));
    }

    @AfterAll
    static void closeTheProfile() {
        if (operations != null) {
            operations.close();
        }
    }

    private Outcome find(String providerKind, String subject) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ApplicationArguments arguments = new DefaultApplicationArguments("--operation=find-accounts-by-external-subject",
                "--provider-kind=" + providerKind, "--external-subject=" + subject);
        for (String name : operations.getBeanDefinitionNames()) {
            Class<?> type = operations.getType(name);
            if (type == null || !ClassUtils.getUserClass(type).getPackageName().startsWith(INTEGRATION)) {
                continue;
            }
            for (Method method : ClassUtils.getUserClass(type).getMethods()) {
                if (method.getName().equals("run") && method.getReturnType() == int.class && method.getParameterCount() == 3
                        && method.getParameterTypes()[0] == ApplicationArguments.class && method.getParameterTypes()[1] == PrintStream.class
                        && method.getParameterTypes()[2] == PrintStream.class) {
                    try {
                        int exit = (int) method.invoke(operations.getBean(name), arguments, new PrintStream(out, true, StandardCharsets.UTF_8),
                                new PrintStream(err, true, StandardCharsets.UTF_8));
                        return new Outcome(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
                    } catch (InvocationTargetException failure) {
                        throw new AssertionError("the command of bean " + name + " threw", failure.getCause());
                    } catch (IllegalAccessException inaccessible) {
                        throw new AssertionError(inaccessible);
                    }
                }
            }
        }
        throw new AssertionError("RULING-P3B-7: no operations command bean in " + INTEGRATION + " in the profile " + PROFILE);
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-15：档案 integration-operations 启动时不执行 Flyway 迁移（上下文中没有 Flyway 与迁移初始化器）")
    void theProfileRunsNoMigration() {
        assertThat(operations.getBeanNamesForType(Flyway.class)).as("spring.flyway.enabled=false in the profile").isEmpty();
        assertThat(operations.getBeanNamesForType(FlywayMigrationInitializer.class)).as("spring.flyway.enabled=false in the profile").isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-15：档案 integration-operations 不注册任何调度任务")
    void theProfileSchedulesNothing() {
        List<String> scheduled = operations.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream()).map(Object::toString).toList();

        assertThat(scheduled).isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-15：档案 integration-operations 有且只有一个集成层启动执行器，命令在该档案下能查到快照中的员工（退出码 0）")
    void theCommandRunsInTheProfile() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);

        Outcome found = find("WECOM_USER_ID", wecomUserIdOf(person));

        assertThat(removedRunners).as("the profile's runner of " + INTEGRATION).hasSize(1);
        assertThat(found.exit()).as(found.err()).isZero();
        assertThat(found.out()).contains(person.toString());
    }
}
