package com.forcitis.one.acceptance.platform.identity.role_versioning;

import com.forcitis.one.OneApplication;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;

/**
 * What the application's main does, plus two read-only probes, started in a child JVM so that the start-up outcome, the exit code and
 * the output are the real ones (the pattern of SingleOperatorModeLauncher). ADR-0034 decision 1 item 7 (时点：对账在上下文刷新的单例初始化阶段
 * 完成，先于任何 ApplicationRunner、事件监听与重投、定时任务、HTTP 就绪): the probes print how many of the six system roles have an open SEED
 * version at the earliest lifecycle phase (before the web server, the scheduler and every other lifecycle bean start) and in the first
 * ApplicationRunner. They are registered by an initializer, never by component scan, so no other test context sees them.
 */
public final class SystemRoleDeclarationLauncher {

    static final String PROBE = "ROLE-DECLARATION-PROBE";
    static final String LIFECYCLE = "lifecycle";
    static final String RUNNER = "runner";

    static final String OPEN_SEED_VERSIONS = """
            select count(*) from platform_identity.role_versions
             where effective_to_at is null and change_channel_kind = 'SEED'
               and (role_id in ('019a0000-0000-7000-8000-000000000101', '019a0000-0000-7000-8000-000000000102', '019a0000-0000-7000-8000-000000000103',
                               '019a0000-0000-7000-8000-000000000104', '019a0000-0000-7000-8000-000000000105', '019a0000-0000-7000-8000-000000000106')
                    or role_id in (select role_id from platform_identity.roles where code in ('personnel_specialist', 'legal_entity_finance_head', 'payroll_specialist')))""";

    private SystemRoleDeclarationLauncher() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OneApplication.class);
        application.addInitializers(context -> {
            GenericApplicationContext generic = (GenericApplicationContext) context;
            generic.registerBean("roleDeclarationLifecycleProbe", SmartLifecycle.class, () -> new LifecycleProbe(generic));
            generic.registerBean("roleDeclarationRunnerProbe", ApplicationRunner.class, () -> new RunnerProbe(generic));
        });
        application.run(args);
    }

    static void report(GenericApplicationContext context, String point) {
        try (Connection connection = context.getBean(DataSource.class).getConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(OPEN_SEED_VERSIONS)) {
            rs.next();
            System.out.println(PROBE + " " + point + " open-seed-versions=" + rs.getLong(1));
        } catch (SQLException | RuntimeException e) {
            System.out.println(PROBE + " " + point + " failed: " + e.getMessage());
        }
    }

    /** Starts in the earliest phase of all lifecycle beans: the web server, the scheduler and the event listener containers start later. */
    static final class LifecycleProbe implements SmartLifecycle {
        private final GenericApplicationContext context;
        private volatile boolean running;

        LifecycleProbe(GenericApplicationContext context) {
            this.context = context;
        }

        @Override
        public void start() {
            report(context, LIFECYCLE);
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return Integer.MIN_VALUE;
        }
    }

    static final class RunnerProbe implements ApplicationRunner, Ordered {
        private final GenericApplicationContext context;

        RunnerProbe(GenericApplicationContext context) {
            this.context = context;
        }

        @Override
        public void run(ApplicationArguments args) {
            report(context, RUNNER);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
