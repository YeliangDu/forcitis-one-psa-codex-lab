package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestMssql;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.application.RegisterEmployeeOnLoginName;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.SyncResult;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.StaffLookup;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * D1-1 (principles audit A 3-H1): a stalled A8 or old-system database must never hold a request thread. The sources are real SQL Server
 * databases behind the real adapters; the stall is a table lock held by the test (WAITFOR cannot sit inside a view or a function, and a
 * held lock is how SQL Server hangs in production: it waits for ever by default), or a CPU-bound view that holds no lock at all.
 * The connection URLs carry no timeout: whatever stops the wait must come from how the application builds its external connections.
 * Budget of an interactive call to an external system: 3 s (user decision 2026-10-15, audit question 3.1) plus 1.5 s of margin.
 * The three read-only logins equal the RDS platform baseline ({@link ReadOnlyBaselineFixture}): the start-up check of ADR37-1 cannot be
 * switched off, so it runs here and must pass.
 */
@SpringBootTest(properties = {
        "flowable.async-executor-activate=false",
        "forcitis.platform.approval.deadline.enabled=false",
        "forcitis.platform.integration.seeyon.views.enabled=true",
        "forcitis.platform.integration.seeyon.views.head-relationship-type-id=7001",
        "forcitis.platform.integration.seeyon.views.regional-receivables-manager-relationship-type-id=7002",
        "forcitis.platform.integration.seeyon.views.delivery-unit-yes-enum-id=9001",
        "forcitis.platform.integration.seeyon.views.fee-earner-yes-enum-id=9002",
        "forcitis.platform.integration.seeyon.views.root-path=00000001",
        "forcitis.platform.integration.legacy.enabled=true"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class StalledExternalSourceIT {

    static final Duration INTERACTIVE_BUDGET = Duration.ofMillis(3_000 + 1_500);
    static final Duration HARD_CAP_INTERACTIVE = Duration.ofSeconds(20);
    /** The sync is a background job: ShedLock frees its lock after 4 minutes, so it must give up long before that. */
    static final Duration BACKGROUND_BUDGET = Duration.ofSeconds(30);
    static final Duration HARD_CAP_BACKGROUND = Duration.ofSeconds(45);

    static final String LOGIN_NAME = "13900008001";
    /** The old-system department of the short-name view. */
    static final long SHORT_NAME_UNIT = 2;
    /** R-1b P3b: EmployeeId wraps a UUID; the old system's ids are then reached through (LEGACY_SYSTEM, ...) source references (RULING-P3B-3). */
    static final boolean UUID_IDS = EmployeeId.class.getRecordComponents()[0].getType() == UUID.class;
    static final long HEAD_TYPE = 7001;
    static final long MANAGER_TYPE = 7002;

    static final String SA_PASSWORD = TestMssql.SA_PASSWORD;
    static final String A8_READER_PASSWORD = "Bb2!" + UUID.randomUUID();
    static final String NAMES_READER_PASSWORD = "Dd4!" + UUID.randomUUID();

    static final PostgreSQLContainer POSTGRES = TestPostgres.container()
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static final GenericContainer<?> MSSQL = TestMssql.shared();

    static final String A8_WORLD = TestMssql.database(StalledExternalSourceIT.class, "a8world").name();
    static final String LEGACY_WORLD = TestMssql.database(StalledExternalSourceIT.class, "legacyworld").name();
    static final String A8_READER = TestMssql.login(StalledExternalSourceIT.class, "a8_reader");
    static final String NAMES_READER = TestMssql.login(StalledExternalSourceIT.class, "names_reader");

    static {
        POSTGRES.start();
        createExternalWorld();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("forcitis.platform.integration.seeyon.views.jdbc-url", () -> url(A8_WORLD));
        registry.add("forcitis.platform.integration.seeyon.views.username", () -> A8_READER);
        registry.add("forcitis.platform.integration.seeyon.views.password", () -> A8_READER_PASSWORD);
        registry.add("forcitis.platform.integration.legacy.jdbc-url", () -> url(LEGACY_WORLD));
        registry.add("forcitis.platform.integration.legacy.username", () -> NAMES_READER);
        registry.add("forcitis.platform.integration.legacy.password", () -> NAMES_READER_PASSWORD);
    }

    @MockitoBean
    WecomGateway wecom;

    @MockitoBean
    AuditLog audit;

    @Autowired
    MockMvc mvc;

    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    RegisterEmployeeOnLoginName loginLookup;

    @Autowired
    OrgSource a8;

    @Autowired
    OrgShortNames names;

    @Autowired
    org.springframework.context.ApplicationContext context;

    static final ExecutorService CALLERS = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "request-thread");
        thread.setDaemon(true);
        return thread;
    });

    final List<Blocker> blockers = new ArrayList<>();

    final List<ViewRestore> viewsToRestore = new ArrayList<>();

    /** The short-name view is read through the (LEGACY_SYSTEM, ORG_UNIT) reference of its department (RULING-P3B-3). */
    @BeforeEach
    void shortNameUnitHasAReference() {
        if (UUID_IDS) {
            try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
                SourceReferencesProbe.of(context).allocate("LEGACY_SYSTEM", "ORG_UNIT", SHORT_NAME_UNIT);
            }
        }
    }

    @AfterEach
    void releaseEverything() throws SQLException {
        blockers.forEach(Blocker::release);
        blockers.clear();
        if (!viewsToRestore.isEmpty()) {
            endReaderSessions();
        }
        for (ViewRestore restore : viewsToRestore) {
            run(restore.database(), "ALTER VIEW " + restore.view() + " AS " + restore.select());
        }
        viewsToRestore.clear();
    }

    /** A statement that is still burning CPU (the defect this test exposes) would keep the view locked against the restore below. */
    static void endReaderSessions() throws SQLException {
        List<Integer> sessions = new ArrayList<>();
        try (Connection c = admin("master"); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT session_id FROM sys.dm_exec_sessions WHERE login_name IN ('" + A8_READER + "', '" + NAMES_READER + "')")) {
            while (rs.next()) {
                sessions.add(rs.getInt(1));
            }
        }
        for (int session : sessions) {
            run("master", "KILL " + session);
        }
    }

    @Test
    @DisplayName("D1-1 对照：外部库不卡时 A8 登录名查得到人、简称读得到、同步成功")
    void controlWithoutAnyStall() throws Exception {
        assertThat(a8.findStaffByLogin(new StaffLookup.ByLoginName(LOGIN_NAME))).hasSize(1);
        assertThat(names.readShortNames()).hasSize(1);
        SyncResult result = runSync();
        assertThat(result.status()).as(result.note()).isNotEqualTo(SyncRunStatus.FAILED);
    }

    @Test
    @DisplayName("D1-1 A8 组织视图卡住时同步失败并告警且不挂住")
    void stalledA8ViewsFailTheSyncWithAnAlert(CapturedOutput output) throws Exception {
        blockers.add(Blocker.hold(A8_WORLD, "dbo.ORG_UNIT"));
        assertBlocked(A8_WORLD, "SELECT COUNT(*) FROM forcitis_one.v_org_unit");

        Outcome<SyncResult> outcome = timed(HARD_CAP_BACKGROUND, () -> runSync());

        assertThat(outcome.hung()).as("the sync is still waiting for the A8 views after %s: its thread and the ShedLock are held", HARD_CAP_BACKGROUND).isFalse();
        assertThat(outcome.failure()).isNull();
        assertThat(outcome.elapsed()).as("the sync gives up long before the 4 minutes of its lock").isLessThanOrEqualTo(BACKGROUND_BUDGET);
        assertThat(outcome.value().status()).as("the run is recorded as failed, the snapshot stays").isEqualTo(SyncRunStatus.FAILED);
        assertThat(output.getAll()).as("a failed sync raises an ALERT line").containsPattern("ALERT[^\\n]*organization\\.workforce[^\\n]*sync");
        assertNoSessionStillWaiting(A8_READER);
    }

    @Test
    @DisplayName("D1-1 A8 视图卡住时登录按登录名查人在预算内报告来源不可用，登录流程继续")
    void stalledA8ViewsDegradeTheLoginLookup() throws Exception {
        blockers.add(Blocker.hold(A8_WORLD, "dbo.ORG_MEMBER"));
        assertBlocked(A8_WORLD, "SELECT COUNT(*) FROM forcitis_one.v_org_member");

        assertLoginLookupDegrades();
        assertNoSessionStillWaiting(A8_READER);
    }

    @Test
    @DisplayName("D1-1 A8 视图查询不加锁只是极慢时登录按登录名查人同样在预算内降级")
    void slowA8ViewWithoutLocksDegradesTheLoginLookup() throws Exception {
        replaceView(A8_WORLD, "forcitis_one.v_org_member", SLOW_MEMBER_VIEW);
        viewsToRestore.add(new ViewRestore(A8_WORLD, "forcitis_one.v_org_member", MEMBER_VIEW));

        assertLoginLookupDegrades();
    }

    /** The port says "unavailable" (that is what the caller turns into "nobody found"), and the login use case built on it returns instead of throwing. */
    private void assertLoginLookupDegrades() {
        Outcome<?> port = timed(HARD_CAP_INTERACTIVE, () -> a8.findStaffByLogin(new StaffLookup.ByLoginName(LOGIN_NAME)));
        assertThat(port.hung()).as("the login lookup is still waiting for A8 after %s: it holds a request thread", HARD_CAP_INTERACTIVE).isFalse();
        assertThat(port.elapsed()).as("login answers within the 3 s budget of an external call").isLessThanOrEqualTo(INTERACTIVE_BUDGET);
        assertThat(port.failure()).as("an unreachable source is reported as such, not as an empty answer").isInstanceOf(ExternalSystemUnavailableException.class);

        Outcome<Boolean> useCase = timed(HARD_CAP_INTERACTIVE, () -> loginLookup.execute(LOGIN_NAME).isPresent());
        assertThat(useCase.hung()).isFalse();
        assertThat(useCase.elapsed()).isLessThanOrEqualTo(INTERACTIVE_BUDGET);
        assertThat(useCase.failure()).isNull();
        assertThat(useCase.value()).isFalse();
    }

    @Test
    @DisplayName("D1-1 三条外部连接的会话都带有限的锁等待上限，交互路径的两条不超过 3 s")
    void everyExternalSessionCarriesALockTimeout() throws Exception {
        a8.findStaffByLogin(new StaffLookup.ByLoginName(LOGIN_NAME));
        names.readShortNames();

        Map<String, Integer> lockTimeouts = new java.util.TreeMap<>();
        try (Connection c = admin("master"); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT login_name, MIN(lock_timeout) FROM sys.dm_exec_sessions "
                        + "WHERE is_user_process = 1 AND login_name IN ('" + A8_READER + "', '" + NAMES_READER + "') GROUP BY login_name")) {
            while (rs.next()) {
                lockTimeouts.put(rs.getString(1), rs.getInt(2));
            }
        }

        assertThat(lockTimeouts).as("each of the three adapters opened a session").containsOnlyKeys(A8_READER, NAMES_READER);
        assertThat(lockTimeouts.get(A8_READER)).as("SQL Server's default -1 waits for ever").isBetween(1, 3_000);
        assertThat(lockTimeouts.get(NAMES_READER)).isGreaterThan(0);
    }

    /**
     * Whatever ends the wait on our side (query timeout, socket timeout, an abandoned future), a statement still burning on the A8 server would keep
     * one of its workers busy after the caller has moved on. The 2 s elapse before the @AfterEach KILL ends the sessions.
     */
    @Test
    @DisplayName("D1-1 复审 H2：D1-1-R2 极慢 A8 视图在登录查人降级后服务器端也被取消")
    void reviewH2_slowA8ViewIsCancelledOnTheServerAfterTheLoginLookupGivesUp() throws Exception {
        Awaitility.await("no request of a8_reader is left over from an earlier case").atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(requestsOf(A8_READER, false)).isZero());
        replaceView(A8_WORLD, "forcitis_one.v_org_member", SLOW_MEMBER_VIEW);
        viewsToRestore.add(new ViewRestore(A8_WORLD, "forcitis_one.v_org_member", MEMBER_VIEW));

        Future<Outcome<Object>> inFlight = CALLERS.submit(() -> timed(HARD_CAP_INTERACTIVE, () -> a8.findStaffByLogin(new StaffLookup.ByLoginName(LOGIN_NAME))));
        Awaitility.await("the slow statement of a8_reader is visible in sys.dm_exec_requests while the lookup waits").atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(50)).untilAsserted(() -> assertThat(requestsOf(A8_READER, false)).isPositive());
        Outcome<Object> lookup = inFlight.get(HARD_CAP_INTERACTIVE.toMillis() * 2, TimeUnit.MILLISECONDS);
        assertThat(lookup.hung()).isFalse();
        assertThat(lookup.elapsed()).isLessThanOrEqualTo(INTERACTIVE_BUDGET);
        assertThat(lookup.failure()).isInstanceOf(ExternalSystemUnavailableException.class);

        Awaitility.await("SQL Server has stopped the slow statement of a8_reader once the login lookup gave up")
                .atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(requestsOf(A8_READER, false)).as("requests of a8_reader still running in sys.dm_exec_requests").isZero());
    }

    static int requestsOf(String login, boolean blockedOnly) throws SQLException {
        try (Connection c = admin("master"); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM sys.dm_exec_requests r JOIN sys.dm_exec_sessions e ON e.session_id = r.session_id "
                        + "WHERE e.login_name = '" + login + "'" + (blockedOnly ? " AND r.blocking_session_id <> 0" : ""))) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** A scheduled job runs inside a work context; the sync records its failure as an event, which needs one. */
    SyncResult runSync() {
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            return sync.execute(SyncTrigger.MANUAL, false);
        }
    }

    static org.springframework.test.web.servlet.request.RequestPostProcessor ordinarySession(UUID userId) {
        return user(userId.toString()).authorities(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
    }

    record Outcome<T>(T value, Throwable failure, Duration elapsed, boolean hung) {
    }

    /** The task runs on its own thread, like a request thread; a task that outlives the hard cap is reported as hung and ends when the lock is released. */
    static <T> Outcome<T> timed(Duration hardCap, Callable<T> task) {
        Future<T> future = CALLERS.submit(task);
        long start = System.nanoTime();
        try {
            T value = future.get(hardCap.toMillis(), TimeUnit.MILLISECONDS);
            return new Outcome<>(value, null, Duration.ofNanos(System.nanoTime() - start), false);
        } catch (TimeoutException hung) {
            return new Outcome<>(null, null, Duration.ofNanos(System.nanoTime() - start), true);
        } catch (ExecutionException failed) {
            return new Outcome<>(null, failed.getCause(), Duration.ofNanos(System.nanoTime() - start), false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    static void assertNoSessionStillWaiting(String login) {
        Awaitility.await("the database keeps no request of " + login + " waiting on a lock after the caller has given up")
                .atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
                    try (Connection c = admin("master"); Statement s = c.createStatement();
                            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM sys.dm_exec_requests r JOIN sys.dm_exec_sessions e ON e.session_id = r.session_id "
                                    + "WHERE e.login_name = '" + login + "' AND r.blocking_session_id <> 0")) {
                        rs.next();
                        assertThat(rs.getInt(1)).isZero();
                    }
                });
    }

    /** Proves the stall is real: a probe with a short lock timeout of its own is refused (error 1222), so a fast answer cannot come from a lock that was never held. */
    static void assertBlocked(String database, String probe) throws SQLException {
        try (Connection c = admin(database); Statement s = c.createStatement()) {
            s.execute("SET LOCK_TIMEOUT 300");
            try {
                s.execute(probe);
                throw new AssertionError("the probe was not blocked: " + probe);
            } catch (SQLException expected) {
                assertThat(expected.getErrorCode()).as("lock request time out period exceeded").isEqualTo(1222);
            }
        }
    }

    static String url(String database) {
        return TestMssql.url(MSSQL, database);
    }

    static Connection admin(String database) throws SQLException {
        return TestMssql.adminConnection(MSSQL, database);
    }

    static void run(String database, String... statements) throws SQLException {
        try (Connection c = admin(database); Statement s = c.createStatement()) {
            for (String sql : statements) {
                s.execute(sql);
            }
        }
    }

    /** A table lock held by an open transaction of the administrator; readers of any view over the table wait for it. */
    static final class Blocker {
        private final Connection connection;

        private Blocker(Connection connection) {
            this.connection = connection;
        }

        static Blocker hold(String database, String table) throws SQLException {
            Connection c = admin(database);
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("SELECT TOP (1) 1 FROM " + table + " WITH (TABLOCKX, HOLDLOCK)");
            }
            return new Blocker(c);
        }

        void release() {
            try {
                connection.rollback();
                connection.close();
            } catch (SQLException ignored) {
                // the connection is gone with the container anyway
            }
        }
    }

    static final String MEMBER_VIEW = "SELECT ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, SUPERVISOR_MEMBER_ID, WE_COM_ID, IS_INTERNAL, "
            + "LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID FROM dbo.ORG_MEMBER";

    /** A scalar function that loops for about a minute: CPU only, no lock, and the optimizer cannot skip it (it is not inlined because of the loop). */
    static final String BURN_FUNCTION = "CREATE FUNCTION dbo.burn() RETURNS int AS BEGIN DECLARE @i int = 0; WHILE @i < 2000000000 SET @i = @i + 1; RETURN @i; END";
    static final String SLOW = " WHERE dbo.burn() > 0";
    static final String SLOW_MEMBER_VIEW = MEMBER_VIEW + SLOW;

    record ViewRestore(String database, String view, String select) {
    }

    static void replaceView(String database, String view, String select) throws SQLException {
        run(database, "ALTER VIEW " + view + " AS " + select);
    }

    private static void createExternalWorld() {
        try {
            ReadOnlyBaselineFixture.createRdsPlatform(StalledExternalSourceIT::run);
            createA8World();
            createLegacyWorld();
            createReadOnlyLogins();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot prepare the external databases; SQL Server log:\n" + MSSQL.getLogs(), e);
        }
    }

    private static void createA8World() throws SQLException {
        run(A8_WORLD, "CREATE SCHEMA " + ReadOnlyBaselineFixture.VIEW_SCHEMA + " AUTHORIZATION dbo",
                BURN_FUNCTION,
                "CREATE TABLE dbo.ORG_UNIT (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, PATH nvarchar(200), IS_DELETED bit, "
                        + "IS_INTERNAL bit, IS_ENABLE bit, CATEGORY nvarchar(16), DELIVERY_FLAG_ENUM_ID nvarchar(32), TYPE nvarchar(32) NULL)",
                "CREATE TABLE dbo.relationship_rows (SOURCE_MEMBER_ID bigint, ORG_UNIT_ID bigint, RELATIONSHIP_TYPE_ID bigint, MEMBER_IS_DELETED bit)",
                "CREATE TABLE dbo.post_rows (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, STATUS int, IS_DELETED bit)",
                "CREATE TABLE dbo.ORG_MEMBER (ID bigint PRIMARY KEY, NAME nvarchar(64), CODE nvarchar(32), ORG_UNIT_ID bigint, ORG_UNIT_PATH nvarchar(200), "
                        + "POST_ID bigint, SUPERVISOR_MEMBER_ID bigint, WE_COM_ID nvarchar(64), IS_INTERNAL bit, LOGIN_NAME nvarchar(64), "
                        + "ENTERPRISE_EMAIL nvarchar(128), GENDER nvarchar(8), FEE_EARNER_ENUM_ID nvarchar(32), STATE smallint NULL, IS_DELETED smallint NULL, IS_ENABLE smallint NULL)",
                "INSERT dbo.ORG_UNIT VALUES (1, 'C1', N'Root', 1, '00000001', 0, 1, 1, N'总部', NULL, N'Account'), (2, 'C2', N'Delivery', 2, '000000010001', 0, 1, 1, N'区域', '9001', N'Department')",
                "INSERT dbo.post_rows VALUES (100, 'P100', N'Consultant', 1, 0, 0)",
                "INSERT dbo.ORG_MEMBER VALUES (8001, N'Worker', 'E8001', 2, '000000010001', 100, NULL, 'wx-8001', 1, '" + LOGIN_NAME + "', 'worker@example.test', N'女', '9002', 1, 0, 1)",
                "INSERT dbo.relationship_rows VALUES (8001, 2, " + HEAD_TYPE + ", 0), (8001, 2, " + MANAGER_TYPE + ", 0)",
                "CREATE VIEW forcitis_one.v_org_unit AS SELECT ID, CODE, NAME, SORT_ID, PATH, IS_DELETED, IS_INTERNAL, IS_ENABLE, CATEGORY, DELIVERY_FLAG_ENUM_ID FROM dbo.ORG_UNIT",
                "CREATE VIEW forcitis_one.v_org_relationship AS SELECT SOURCE_MEMBER_ID, ORG_UNIT_ID, RELATIONSHIP_TYPE_ID, MEMBER_IS_DELETED FROM dbo.relationship_rows",
                "CREATE VIEW forcitis_one.v_org_post AS SELECT ID, CODE, NAME, SORT_ID, STATUS, IS_DELETED FROM dbo.post_rows",
                "CREATE VIEW forcitis_one.v_org_member AS " + MEMBER_VIEW);
    }

    private static void createLegacyWorld() throws SQLException {
        run(LEGACY_WORLD, BURN_FUNCTION,
                "CREATE TABLE dbo.short_name_rows (id bigint, short_name nvarchar(64))",
                "INSERT dbo.short_name_rows VALUES (2, N'Delivery')",
                "CREATE VIEW dbo.v_one_legacy_org_short_name AS SELECT id, short_name FROM dbo.short_name_rows");
    }

    /** Checked against the RDS export before any case runs: a fixture that drifted from the baseline would prove nothing about production. */
    private static void createReadOnlyLogins() throws SQLException {
        ReadOnlyBaselineFixture.createViewSchemaReader(StalledExternalSourceIT::run, A8_WORLD, A8_READER, A8_READER_PASSWORD);
        ReadOnlyBaselineFixture.createSingleViewReader(StalledExternalSourceIT::run, LEGACY_WORLD, "dbo.v_one_legacy_org_short_name", NAMES_READER,
                NAMES_READER_PASSWORD);
        ReadOnlyBaselineFixture.assertMatchesTheRdsBaseline(url(A8_WORLD), A8_WORLD, A8_READER, A8_READER_PASSWORD);
        ReadOnlyBaselineFixture.assertMatchesTheRdsBaseline(url(LEGACY_WORLD), LEGACY_WORLD, NAMES_READER, NAMES_READER_PASSWORD);
    }
}
