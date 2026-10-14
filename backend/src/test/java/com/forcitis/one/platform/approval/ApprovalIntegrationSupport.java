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
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import com.forcitis.one.platform.approval.events.ApprovalTaskOverdue;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskResulted;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One PostgreSQL 18 container per test JVM and a cached application context; the Flowable schema is created by the engine only in tests; other modules' dependencies are doubles. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "forcitis.platform.approval.deadline.enabled=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(ApprovalIntegrationSupport.Doubles.class)
public abstract class ApprovalIntegrationSupport {

    public static final long ADMINISTRATOR = 9001;
    public static final LegalEntityId LEGAL_ENTITY = new LegalEntityId(1);
    static final AtomicLong IDS = new AtomicLong(100_000);

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    /** Throw-away credentials for the stand-in read-only account, generated per run. */
    static final String LEGACY_PASSWORD = UUID.randomUUID().toString();

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

    @MockitoBean
    protected DocumentNumbers numbers;

    @MockitoBean
    protected com.forcitis.one.organization.workforce.api.EmployeeLegalEntities employeeLegalEntities;

    @Autowired
    protected ApprovalProcesses approvals;

    @Autowired
    protected FakeOrganisation organisation;

    @Autowired
    protected ApplicationEvents recorded;

    @Autowired
    protected FlakyListener flaky;

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

    protected Events events;

    private final List<Runnable> variantCleanup = new java.util.ArrayList<>();

    /** Deploys a test variant of the timesheet process as the newest version; removed after the test. */
    protected void deployVariant(String directory) {
        String bpmnResource = directory + "/cost_timesheet_approval.bpmn20.xml";
        if (getClass().getClassLoader().getResource(bpmnResource) == null) {
            throw new IllegalArgumentException("No process variant " + bpmnResource);
        }
        String bpmn = repository.createDeployment().addClasspathResource(bpmnResource).deploy().getId();
        variantCleanup.add(() -> repository.deleteDeployment(bpmn, true));
    }

    @org.junit.jupiter.api.AfterEach
    void removeVariants() {
        java.util.Collections.reverse(variantCleanup);
        variantCleanup.forEach(Runnable::run);
        variantCleanup.clear();
    }

    static final AtomicLong NUMBERS = new AtomicLong();

    @BeforeEach
    void administratorExists() {
        org.mockito.Mockito.when(employeeLegalEntities.on(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(LEGAL_ENTITY);
        com.forcitis.one.fixture.doubles.AggregateWriteGuard.forget();
        events = new Events(recorded);
        organisation.person(ADMINISTRATOR, 1, null).role("process_administrator", ADMINISTRATOR);
        org.mockito.Mockito.when(numbers.next(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> new DocumentNumber("TS-T-" + NUMBERS.incrementAndGet()));
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
        return tx.execute(status -> approvals.start(startRequest(businessKey, new UserId(initiator), flagNames,
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(manager)))))));
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

    /** The round a person starting the document again would get: the running one while it runs, the next one after it ended. */
    protected ApprovalStartRequest startRequest(String key, UserId initiator, Set<String> flags, List<ApprovalField> fields) {
        return startRequest(key, initiator, flags, fields, nextRoundOf(key));
    }

    protected static ApprovalStartRequest startRequest(String key, UserId initiator, Set<String> flags, List<ApprovalField> fields, int round) {
        return new ApprovalStartRequest(ProcessKey.COST_TIMESHEET_APPROVAL, document(key), LEGAL_ENTITY, initiator, new RoundNumber(round),
                UUID.randomUUID(), flags, fields, new ContentHash("hash"), new ApprovalSummary("summary of " + key));
    }

    protected int nextRoundOf(String key) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select current_round, status from platform_approval.approval_requests where document_key = ?", key);
        if (rows.isEmpty()) {
            return 1;
        }
        int round = ((Number) rows.getFirst().get("current_round")).intValue();
        return "RUNNING".equals(rows.getFirst().get("status")) ? round : round + 1;
    }

    /** If-Match for a task action sent over HTTP: the version of the task's request as it is now, or 1 for a task nobody knows. */
    protected org.springframework.test.web.servlet.request.RequestPostProcessor currentEtag() {
        return request -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/approval-tasks/([^/:]+):(claim|release|decide|return|transfer|reassign)$")
                    .matcher(request.getRequestURI());
            if (m.find() && request.getHeader("If-Match") == null) {
                List<Long> versions = jdbc.queryForList("select r.version from platform_approval.approval_requests r join platform_approval.approval_tasks t"
                        + " on t.process_key = r.process_key and t.document_key = r.document_key where t.task_id = ?", Long.class, m.group(1));
                request.addHeader("If-Match", "\"" + (versions.isEmpty() ? 1L : versions.getFirst()) + "\"");
            }
            return request;
        };
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

    /** Makes the commit of a decision fail on demand, to prove that everything the decision did is one transaction. */
    public static class CommitGate {
        public volatile boolean failing;

        @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT)
        void on(TaskResulted event) {
            if (failing) {
                throw new IllegalStateException("the event store is down");
            }
        }
    }

    protected void actAs(long user) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(String.valueOf(user), "n/a", List.of()));
    }

    @org.junit.jupiter.api.AfterEach
    void oneAggregatePerTransaction() {
        List<String> violations = com.forcitis.one.fixture.doubles.AggregateWriteGuard.violations();
        com.forcitis.one.fixture.doubles.AggregateWriteGuard.forget();
        org.assertj.core.api.Assertions.assertThat(violations).as("DDD-1 one transaction changes one aggregate").isEmpty();
    }

    @org.junit.jupiter.api.AfterEach
    void signOut() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /** The events this test's own thread published, by type: what the module published is seen without waiting for any listener. */
    public static class Events {
        private final ApplicationEvents recorded;

        Events(ApplicationEvents recorded) {
            this.recorded = recorded;
        }

        public List<ApprovalCompleted> completed() {
            return recorded.stream(ApprovalCompleted.class).toList();
        }

        public List<TaskResulted> resulted() {
            return recorded.stream(TaskResulted.class).toList();
        }

        public List<TaskAssigned> assigned() {
            return recorded.stream(TaskAssigned.class).toList();
        }

        public List<ApprovalTaskOverdue> overdue() {
            return recorded.stream(ApprovalTaskOverdue.class).toList();
        }

        public List<ApprovalTaskEscalated> escalated() {
            return recorded.stream(ApprovalTaskEscalated.class).toList();
        }

        public <T> List<T> of(Class<T> type) {
            return recorded.stream(type).toList();
        }
    }

    /** A business module's listener that fails the first delivery of a document called flaky-*, so the registry's redelivery can be shown. */
    public static class FlakyListener {
        private final List<ApprovalCompleted> delivered = new CopyOnWriteArrayList<>();
        private final Set<String> failedOnce = java.util.concurrent.ConcurrentHashMap.newKeySet();

        public List<ApprovalCompleted> delivered() {
            return delivered;
        }

        public Set<String> failedOnce() {
            return failedOnce;
        }

        @ApplicationModuleListener
        void on(ApprovalCompleted event) {
            if (event.document().documentId().value().startsWith("flaky-") && failedOnce.add(event.document().documentId().value())) {
                throw new IllegalStateException("first delivery fails on purpose");
            }
            delivered.add(event);
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
        @Primary
        CurrentUser administratorUnlessSignedIn() {
            return new CurrentUser() {
                @Override
                public UserId userId() {
                    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    return authentication != null && authentication.isAuthenticated() ? new UserId(Long.parseLong(authentication.getName()))
                            : new UserId(ADMINISTRATOR);
                }

                @Override
                public LegalEntityId legalEntityId() {
                    return LEGAL_ENTITY;
                }
            };
        }

        @Bean
        com.forcitis.one.fixture.doubles.AggregateWriteGuard aggregateWriteGuard() {
            return new com.forcitis.one.fixture.doubles.AggregateWriteGuard();
        }

        @Bean
        CommitGate commitGate() {
            return new CommitGate();
        }

        @Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }

        @Bean
        ScriptedHook scriptedHook() {
            return new ScriptedHook();
        }
    }
}
