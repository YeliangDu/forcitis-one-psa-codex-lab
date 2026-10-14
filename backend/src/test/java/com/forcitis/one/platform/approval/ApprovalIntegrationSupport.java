package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalHook;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
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

/** One PostgreSQL 18 container per test JVM and a cached application context; the Flowable schema is created by the engine only in tests; other modules' dependencies are doubles. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "forcitis.platform.approval.deadline.enabled=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=" + ApprovalIntegrationSupport.ADMINISTRATOR,
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
@Import(ApprovalIntegrationSupport.Doubles.class)
public abstract class ApprovalIntegrationSupport {

    public static final long ADMINISTRATOR = 9001;
    public static final LegalEntityId LEGAL_ENTITY = new LegalEntityId(1);
    static final AtomicLong IDS = new AtomicLong(100_000);

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    /** Throw-away credentials for the stand-in read-only account, generated per run. */
    static final String LEGACY_PASSWORD = java.util.UUID.randomUUID().toString();

    static {
        POSTGRES.start();
        prepareLegacyStandIn();
    }

    /** Stand-in for the old inbox view, with a read-only account that can SELECT that view and nothing else. */
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

    @Autowired
    protected org.flowable.engine.RepositoryService repository;

    @Autowired
    protected org.flowable.dmn.api.DmnRepositoryService dmnRepository;

    private final List<Runnable> variantCleanup = new java.util.ArrayList<>();

    /** Deploys a test variant of the timesheet process and / or its decision table (whichever the directory has) as the newest version; removed after the test. */
    protected void deployVariant(String directory) {
        String bpmnResource = directory + "/cost_timesheet_approval.bpmn20.xml";
        if (getClass().getClassLoader().getResource(bpmnResource) != null) {
            String bpmn = repository.createDeployment().addClasspathResource(bpmnResource).deploy().getId();
            variantCleanup.add(() -> repository.deleteDeployment(bpmn, true));
        }
        String dmnResource = directory + "/cost_timesheet_approval_nodes.dmn";
        if (getClass().getClassLoader().getResource(dmnResource) != null) {
            String dmn = dmnRepository.createDeployment().addClasspathResource(dmnResource).deploy().getId();
            variantCleanup.add(() -> dmnRepository.deleteDeployment(dmn));
        }
    }

    @org.junit.jupiter.api.AfterEach
    void removeVariants() {
        java.util.Collections.reverse(variantCleanup);
        variantCleanup.forEach(Runnable::run);
        variantCleanup.clear();
    }

    @BeforeEach
    void administratorExists() {
        organisation.person(ADMINISTRATOR, 1, null);
    }

    /** Unique ids so tests sharing database and context cannot see each other's data. */
    protected static long newId() {
        return IDS.incrementAndGet();
    }

    protected static String newKey(String prefix) {
        return prefix + "-" + newId();
    }

    protected ApprovalInstanceId start(String businessKey, long initiator, long manager) {
        return start(businessKey, initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS);
    }

    protected ApprovalInstanceId startWithSupervisor(String businessKey, long initiator, long manager) {
        return start(businessKey, initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS, TimesheetRoutingFlag.OVERHEAD_HOURS);
    }

    protected ApprovalInstanceId start(String businessKey, long initiator, long manager, TimesheetRoutingFlag... flags) {
        Set<String> flagNames = java.util.Arrays.stream(flags).map(TimesheetRoutingFlag::flagName).collect(java.util.stream.Collectors.toSet());
        return tx.execute(status -> approvals.start(new ApprovalStartRequest(ProcessKey.COST_TIMESHEET_APPROVAL, document(businessKey),
                LEGAL_ENTITY, new UserId(initiator), flagNames,
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(manager)))), "digest")));
    }

    protected Task openTask(ApprovalInstanceId instance) {
        return taskService.createTaskQuery().processInstanceId(instance.value()).singleResult();
    }

    protected Task openTask(ApprovalInstanceId instance, String nodeKey) {
        return taskService.createTaskQuery().processInstanceId(instance.value()).taskDefinitionKey(nodeKey).singleResult();
    }

    protected ApprovalTaskId openTaskId(ApprovalInstanceId instance, String nodeKey) {
        return new ApprovalTaskId(openTask(instance, nodeKey).getId());
    }

    protected ApprovalTaskId openTaskId(ApprovalInstanceId instance) {
        return new ApprovalTaskId(openTask(instance).getId());
    }

    protected static ApprovalStartRequest startRequest(String key, UserId initiator, Set<String> flags, List<ApprovalField> fields) {
        return new ApprovalStartRequest(ProcessKey.COST_TIMESHEET_APPROVAL, document(key), LEGAL_ENTITY, initiator, flags, fields, "digest");
    }

    protected static DocumentReference document(String key) {
        return new DocumentReference(DocumentType.TIMESHEET, new DocumentId(key));
    }

    protected String requestStatus(String key) {
        return jdbc.queryForObject("select status from platform_approval.approval_requests where document_key = ?", String.class, key);
    }

    protected static void eventually(Runnable assertion) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(assertion::run);
    }

    public static class EventRecorder {
        // The bean is proxied (@Async listener methods): tests read through the accessor methods, not the fields.
        private final List<ApprovalCompleted> completed = new CopyOnWriteArrayList<>();
        private final List<TaskResulted> resulted = new CopyOnWriteArrayList<>();
        private final List<TaskAssigned> assigned = new CopyOnWriteArrayList<>();
        private final List<com.forcitis.one.platform.approval.events.ApprovalTaskOverdue> overdue = new CopyOnWriteArrayList<>();
        private final List<com.forcitis.one.platform.approval.events.ApprovalTaskEscalated> escalated = new CopyOnWriteArrayList<>();
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

        public List<com.forcitis.one.platform.approval.events.ApprovalTaskOverdue> overdue() {
            return overdue;
        }

        public List<com.forcitis.one.platform.approval.events.ApprovalTaskEscalated> escalated() {
            return escalated;
        }

        @ApplicationModuleListener
        void on(com.forcitis.one.platform.approval.events.ApprovalTaskOverdue event) {
            overdue.add(event);
        }

        @ApplicationModuleListener
        void on(com.forcitis.one.platform.approval.events.ApprovalTaskEscalated event) {
            escalated.add(event);
        }

        public Set<String> failedOnce() {
            return failedOnce;
        }

        @ApplicationModuleListener
        void on(ApprovalCompleted event) {
            // A business module's listener may fail; the registry keeps the publication and redelivers it.
            if (event.document().documentId().value().startsWith("flaky-") && failedOnce.add(event.document().documentId().value())) {
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
