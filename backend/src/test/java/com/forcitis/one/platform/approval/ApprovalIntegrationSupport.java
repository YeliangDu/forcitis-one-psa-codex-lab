package com.forcitis.one.platform.approval;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalHook;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskResulted;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared set-up of the platform.approval integration tests: one PostgreSQL 18 container for the whole test JVM, the full
 * application context (cached across the test classes), Flowable's schema created by the engine (tests set
 * flowable.database-schema-update=true; production does not), and doubles for what other modules provide: a synthetic
 * organisation (OrgDirectory + RoleDirectory), WecomGateway and AuditLog. No real system is contacted.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=" + ApprovalIntegrationSupport.ADMINISTRATOR,
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
@Import(ApprovalIntegrationSupport.Doubles.class)
public abstract class ApprovalIntegrationSupport {

    public static final long ADMINISTRATOR = 9001;
    static final AtomicLong IDS = new AtomicLong(100_000);

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    /** Throw-away credentials of the stand-in for the old system's read-only account (generated per run, never a real secret). */
    static final String LEGACY_PASSWORD = java.util.UUID.randomUUID().toString();

    static {
        POSTGRES.start();
        prepareLegacyStandIn();
    }

    /**
     * A stand-in for the old system's inbox view in the same container: a table of in-progress task rows, the view the new
     * system may read, and a read-only account that can SELECT that view and nothing else (REIMAGINED_ARCHITECTURE §3.6).
     */
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
        registry.add("forcitis.platform.approval.legacy.url", POSTGRES::getJdbcUrl);
        registry.add("forcitis.platform.approval.legacy.username", () -> "legacy_ro");
        registry.add("forcitis.platform.approval.legacy.password", () -> LEGACY_PASSWORD);
        registry.add("forcitis.platform.approval.legacy.view-name", () -> "legacy_fixture.v_psa_one_legacy_todo");
        registry.add("forcitis.platform.approval.legacy.detail-url-template",
                () -> "https://legacy.example.test/bpm/detail?instance={processInstanceId}&task={taskId}");
    }

    @MockitoBean
    protected WecomGateway wecom;

    @MockitoBean
    protected AuditLog audit;

    @Autowired
    protected ApprovalProcesses approvals;

    @Autowired
    protected FakeOrganisation organisation;

    @Autowired
    protected EventRecorder events;

    @Autowired
    protected TransactionTemplate tx;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RuntimeService runtime;

    @Autowired
    protected org.flowable.engine.TaskService taskService;

    @BeforeEach
    void administratorExists() {
        organisation.person(ADMINISTRATOR, 1, null);
    }

    /** A user id nobody else uses, so tests sharing the database and context cannot see each other's data. */
    protected static long newId() {
        return IDS.incrementAndGet();
    }

    protected static String newKey(String prefix) {
        return prefix + "-" + newId();
    }

    protected ApprovalInstanceId start(String businessKey, long initiator, long manager) {
        return tx.execute(status -> approvals.start(new ApprovalRequest(ProcessKey.COST_TIMESHEET_APPROVAL, businessKey,
                new UserId(initiator), Set.of(), List.of(new ApprovalField("manager_id", List.of(String.valueOf(manager)))))));
    }

    protected Task openTask(ApprovalInstanceId instance) {
        return taskService.createTaskQuery().processInstanceId(instance.value()).singleResult();
    }

    protected ApprovalTaskId openTaskId(ApprovalInstanceId instance) {
        return new ApprovalTaskId(openTask(instance).getId());
    }

    protected String requestStatus(String businessKey) {
        return jdbc.queryForObject("select status from platform_approval.approval_requests where business_key = ?", String.class,
                businessKey);
    }

    protected static void eventually(Runnable assertion) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(assertion::run);
    }

    /** Records module events (they are delivered through the event publication registry, like a business module would get them). */
    public static class EventRecorder {
        // The bean is proxied (@Async listener methods): tests read through the accessor methods, not the fields.
        private final List<ApprovalCompleted> completed = new CopyOnWriteArrayList<>();
        private final List<TaskResulted> resulted = new CopyOnWriteArrayList<>();
        private final List<TaskAssigned> assigned = new CopyOnWriteArrayList<>();
        private final Set<String> failedOnce = java.util.concurrent.ConcurrentHashMap.newKeySet();

        public List<ApprovalCompleted> completed() {
            return completed;
        }

        public List<TaskResulted> resulted() {
            return resulted;
        }

        public List<TaskAssigned> assigned() {
            return assigned;
        }

        public Set<String> failedOnce() {
            return failedOnce;
        }

        @ApplicationModuleListener
        void on(ApprovalCompleted event) {
            // A business module's listener may fail; the registry keeps the publication and redelivers it (RULE-295/303).
            if (event.businessKey().startsWith("flaky-") && failedOnce.add(event.businessKey())) {
                throw new IllegalStateException("first delivery fails on purpose");
            }
            completed.add(event);
        }

        @ApplicationModuleListener
        void on(TaskResulted event) {
            resulted.add(event);
        }

        @ApplicationModuleListener
        void on(TaskAssigned event) {
            assigned.add(event);
        }
    }

    /** A scripted BEFORE hook for the tests; production hooks come from business modules. */
    public static class ScriptedHook implements ApprovalHook {
        public volatile com.forcitis.one.platform.approval.api.HookDecision next = com.forcitis.one.platform.approval.api.HookDecision.allow();
        public final List<com.forcitis.one.platform.approval.api.HookContext> calls = new CopyOnWriteArrayList<>();
        public volatile java.util.function.Consumer<com.forcitis.one.platform.approval.api.HookContext> onCall;

        @Override
        public String hookKey() {
            return "TEST_DEPARTMENT_REVIEW";
        }

        @Override
        public com.forcitis.one.platform.approval.api.HookDecision before(com.forcitis.one.platform.approval.api.HookContext context) {
            calls.add(context);
            if (onCall != null) {
                onCall.accept(context);
            }
            return next;
        }
    }

    @TestConfiguration
    public static class Doubles {

        @Bean
        @Primary
        FakeOrganisation fakeOrganisation() {
            return new FakeOrganisation();
        }

        @Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }

        @Bean
        ScriptedHook scriptedHook() {
            return new ScriptedHook();
        }
    }
}
