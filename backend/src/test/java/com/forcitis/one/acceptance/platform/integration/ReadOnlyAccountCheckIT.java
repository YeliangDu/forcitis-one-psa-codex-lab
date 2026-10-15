package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.OneApplication;
import com.forcitis.one.TestImages;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.application.RefreshShortNames;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.SyncResult;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.forcitis.one.platform.integration.api.OrgSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR37-1 (ADR-0037 item 4, implementation card of one-coordinator and one-ops 2026-10-15): every read-only external connection is checked
 * against the RDS platform baseline when the application starts and again before each sync; anything more, anything less or anything that
 * cannot be determined refuses. The application is started for real (black box) against a real SQL Server, one start per case.
 *
 * <p>The logins come from {@link ReadOnlyBaselineFixture}; {@link #fixtureReproducesTheRdsBaseline} proves they equal the RDS export
 * before anything else is believed.
 */
@ExtendWith(OutputCaptureExtension.class)
class ReadOnlyAccountCheckIT {

    static final String LOGIN_NAME = "13900008001";
    static final long HEAD_TYPE = 7001;
    static final long MANAGER_TYPE = 7002;

    static final String SA_PASSWORD = "Aa1!" + UUID.randomUUID();
    static final AtomicInteger LOGINS = new AtomicInteger();

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestImages.startingWith("postgres:"))
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static final GenericContainer<?> MSSQL = new GenericContainer<>(TestImages.startingWith(TestImages.MSSQL_PREFIX))
            .withEnv("ACCEPT_EULA", "Y").withEnv("MSSQL_SA_PASSWORD", SA_PASSWORD).withExposedPorts(1433)
            .withStartupTimeout(Duration.ofMinutes(5))
            .waitingFor(Wait.forLogMessage(".*SQL Server is now ready for client connections.*", 1));

    static {
        POSTGRES.start();
        MSSQL.start();
        createExternalWorld();
    }

    enum ReadOnlyConnection {
        A8("a8world", "a8_ro", ReadOnlyBaselineFixture.VIEW_SCHEMA, "dbo.member_rows"),
        LEGACY_APPROVAL("legacyworld", "todo_ro", "dbo", "dbo.todo_rows"),
        LEGACY_NAMES("legacyworld", "names_ro", "dbo", "dbo.short_name_rows");

        final String database;
        final String loginPrefix;
        final String viewSchema;
        /** A base table under that view; the database-level GRANT SELECT makes it readable to the login as well. */
        final String baseTable;

        ReadOnlyConnection(String database, String loginPrefix, String viewSchema, String baseTable) {
            this.database = database;
            this.loginPrefix = loginPrefix;
            this.viewSchema = viewSchema;
            this.baseTable = baseTable;
        }
    }

    record Account(ReadOnlyConnection connection, String login, String password) {
    }

    record Sql(String database, String statement) {
    }

    /**
     * One way an account can hold more (or less) than the baseline; items are the ADR-0037 item 4 letters any of which may be reported.
     * undo is only for grants that reach beyond the case's own login (to public), so later cases in the same database start clean.
     */
    record Violation(String name, String items, Function<Account, List<Sql>> apply, Function<Account, List<Sql>> undo) {

        Violation(String name, String items, Function<Account, List<Sql>> apply) {
            this(name, items, apply, a -> List.of());
        }

        Pattern reported() {
            return Pattern.compile("[(（][" + items + "][)）]");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final List<Violation> VIOLATIONS = List.of(
            new Violation("库级 DENY INSERT 被撤并 GRANT INSERT ON SCHEMA::dbo", "b", a -> List.of(
                    new Sql(a.connection().database, "REVOKE INSERT FROM " + q(a.login())),
                    new Sql(a.connection().database, "GRANT INSERT ON SCHEMA::dbo TO " + q(a.login())))),
            new Violation("多出一项库级权限 VIEW DEFINITION", "ab", a -> List.of(
                    new Sql(a.connection().database, "GRANT VIEW DEFINITION TO " + q(a.login())))),
            new Violation("多出一项服务器级权限 CREATE ANY DATABASE", "d", a -> List.of(
                    new Sql("master", "GRANT CREATE ANY DATABASE TO " + q(a.login())))),
            new Violation("多出服务器级 CONTROL SERVER", "adf", a -> List.of(
                    new Sql("master", "GRANT CONTROL SERVER TO " + q(a.login())))),
            new Violation("加入固定服务器角色 securityadmin（有效权限不变，ADR (d) IS_SRVROLEMEMBER）", "d", a -> List.of(
                    new Sql("master", "ALTER SERVER ROLE securityadmin ADD MEMBER " + q(a.login())))),
            new Violation("class 4 授权 IMPERSONATE ON USER::可写用户", "e", a -> List.of(
                    new Sql(a.connection().database, "CREATE USER " + q(a.login() + "_writer") + " WITHOUT LOGIN"),
                    new Sql(a.connection().database, "ALTER ROLE db_datawriter ADD MEMBER " + q(a.login() + "_writer")),
                    new Sql(a.connection().database, "GRANT IMPERSONATE ON USER::" + q(a.login() + "_writer") + " TO " + q(a.login())))),
            new Violation("账号拥有一个 schema", "e", a -> List.of(
                    new Sql(a.connection().database, "CREATE SCHEMA " + q(a.login() + "_owned") + " AUTHORIZATION " + q(a.login())))),
            new Violation("账号拥有一个角色", "e", a -> List.of(
                    new Sql(a.connection().database, "CREATE ROLE " + q(a.login() + "_owned_role") + " AUTHORIZATION " + q(a.login())))),
            new Violation("加入只有 SELECT 的自定义角色", "c", a -> List.of(
                    new Sql(a.connection().database, "CREATE ROLE " + q(a.login() + "_readers")),
                    new Sql(a.connection().database, "GRANT SELECT ON SCHEMA::dbo TO " + q(a.login() + "_readers")),
                    new Sql(a.connection().database, "ALTER ROLE " + q(a.login() + "_readers") + " ADD MEMBER " + q(a.login())))),
            new Violation("加入 db_datareader", "c", a -> List.of(
                    new Sql(a.connection().database, "ALTER ROLE db_datareader ADD MEMBER " + q(a.login())))),
            new Violation("能访问基线外的另一个用户库", "f", a -> List.of(
                    new Sql("otherworld", "CREATE USER " + q(a.login()) + " FOR LOGIN " + q(a.login())))),
            new Violation("缺少一项：DENY VIEW SERVER STATE（REVOKE 无效：平台授予的 ALTER SERVER STATE 隐含它）", "ad", a -> List.of(
                    new Sql("master", "DENY VIEW SERVER STATE TO " + q(a.login())))),
            new Violation("校验查询失败：sys.database_permissions 被拒绝读取", "be", a -> List.of(
                    new Sql(a.connection().database, "DENY SELECT ON sys.database_permissions TO " + q(a.login())))),
            new Violation("复审 H-1：对象级 GRANT TAKE OWNERSHIP ON OBJECT::可读底表（ALTER AUTHORIZATION 后成为所有者）", "g", a -> List.of(
                    new Sql(a.connection().database, "GRANT TAKE OWNERSHIP ON OBJECT::" + a.connection().baseTable + " TO " + q(a.login())))),
            new Violation("复审 H-1：schema 级 GRANT CONTROL ON SCHEMA::所读视图的 schema", "g", a -> List.of(
                    new Sql(a.connection().database, "GRANT CONTROL ON SCHEMA::" + a.connection().viewSchema + " TO " + q(a.login())))),
            new Violation("复审 H-1：服务器上针对登录的授权 GRANT IMPERSONATE ON LOGIN::sa（fn_my_permissions 不列）", "d", a -> List.of(
                    new Sql("master", "GRANT IMPERSONATE ON LOGIN::sa TO " + q(a.login())))),
            new Violation("复审 H-1：public 角色在用户对象上的授权 GRANT UPDATE ON 可读底表 TO public", "g", a -> List.of(
                    new Sql(a.connection().database, "GRANT UPDATE ON " + a.connection().baseTable + " TO public")), a -> List.of(
                    new Sql(a.connection().database, "REVOKE UPDATE ON " + a.connection().baseTable + " FROM public"))),
            new Violation("复审 H-2：在 msdb 有该登录自己的用户并且是 db_owner（不经 guest 进入）", "f", a -> List.of(
                    new Sql("msdb", "CREATE USER " + q(a.login()) + " FOR LOGIN " + q(a.login())),
                    new Sql("msdb", "ALTER ROLE db_owner ADD MEMBER " + q(a.login())))),
            new Violation("复审 H-2：在 master 有该登录自己的用户（无角色；系统库只能经 guest 进入）", "f", a -> List.of(
                    new Sql("master", "CREATE USER " + q(a.login()) + " FOR LOGIN " + q(a.login())))));

    static Stream<Arguments> everyViolationOnEveryConnection() {
        return VIOLATIONS.stream().flatMap(v -> Stream.of(ReadOnlyConnection.values()).map(c -> Arguments.of(c, v)));
    }

    @Test
    @DisplayName("ADR37-1 夹具：容器里新建的三类只读登录与 RDS 实测基线逐项相等（服务器级、库级、角色、可访问的库、库级 DENY 与 GRANT）")
    void fixtureReproducesTheRdsBaseline() throws SQLException {
        for (ReadOnlyConnection connection : ReadOnlyConnection.values()) {
            assertMatchesTheRdsBaseline(baselineAccount(connection));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("ADR37-1 对照：符合 RDS 基线的只读账号，只启用该连接时应用正常启动")
    void baselineAccountStarts(ReadOnlyConnection connection) {
        Account account = baselineAccount(connection);

        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                assertThat(context.isActive()).isTrue();
            }
        });

        assertThat(failure).as("an account shaped exactly like the RDS export must pass the check; refused with:\n%s", messages(failure)).isNull();
    }

    @Test
    @DisplayName("ADR37-1 对照：三条只读连接同时启用、账号都符合基线时应用正常启动")
    void allThreeBaselineAccountsStartTogether() {
        Map<ReadOnlyConnection, Account> accounts = new EnumMap<>(ReadOnlyConnection.class);
        for (ReadOnlyConnection connection : ReadOnlyConnection.values()) {
            accounts.put(connection, baselineAccount(connection));
        }

        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext context = start(accounts)) {
                assertThat(context.isActive()).isTrue();
            }
        });

        assertThat(failure).as("refused with:\n%s", messages(failure)).isNull();
    }

    @ParameterizedTest(name = "{0}：{1}")
    @MethodSource("everyViolationOnEveryConnection")
    @DisplayName("ADR37-1 反例：账号偏离基线时拒绝启动，并按项报出 (a)–(g) 中不满足的一项")
    void deviationRefusesStartup(ReadOnlyConnection connection, Violation violation) throws SQLException {
        Account account = baselineAccount(connection);
        assertMatchesTheRdsBaseline(account);
        Throwable failure;
        try {
            for (Sql sql : violation.apply().apply(account)) {
                run(sql.database(), sql.statement());
            }
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            for (Sql sql : violation.undo().apply(account)) {
                run(sql.database(), sql.statement());
            }
        }

        assertThat(failure).as("%s on the %s connection: the application started anyway", violation, connection).isNotNull();
        assertThat(messages(failure)).as("the refusal names the unmet item, one of (%s)", violation.items()).containsPattern(violation.reported());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("ADR37-1 复审 L（删改测试覆盖）：只读连接配成新系统自己的 PostgreSQL 地址与账号，经真实工厂拒绝启动，并按项报出不满足的一项")
    void postgresqlAddressIsRefusedThroughTheRealFactory(ReadOnlyConnection connection) {
        Account ownAccount = new Account(connection, POSTGRES.getUsername(), POSTGRES.getPassword());

        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext context = start(Map.of(connection, ownAccount), c -> POSTGRES.getJdbcUrl())) {
                assertThat(context.isActive()).isTrue();
            }
        });

        assertThat(failure).as("the application's own PostgreSQL account configured as the %s connection: the application started anyway", connection)
                .isNotNull();
        assertThat(messages(failure)).as("the refusal comes from the read-only check and names an unmet item")
                .containsPattern("[(（][a-g][)）]");
    }

    @Test
    @DisplayName("ADR37-1 运行期重检：启动后 A8 账号多了一项写权限，下一次组织同步开始前重检失败，A8 适配器停止并写 ALERT，不再读取")
    void writeGrantedAfterStartupStopsTheA8AdapterBeforeTheNextSync(CapturedOutput output) throws SQLException {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        try (ConfigurableApplicationContext context = start(Map.of(ReadOnlyConnection.A8, account))) {
            OrgSource a8 = context.getBean(OrgSource.class);
            assertThat(a8.findStaffByLoginName(LOGIN_NAME)).as("the adapter reads before the grant changes").hasSize(1);

            grantWriteBehindTheDatabaseDeny(account);
            long readsBefore = baseTableReads("a8world", "org_unit_rows", "relationship_rows", "post_rows", "member_rows");
            SyncResult result = runSync(context.getBean(SyncOrgSnapshot.class));

            assertThat(result.status()).as(result.note()).isEqualTo(SyncRunStatus.FAILED);
            assertThat(output.getAll()).as("the failed re-check raises an ALERT naming item (b)").containsPattern("ALERT[^\\n]*[(（]b[)）]");
            assertThat(baseTableReads("a8world", "org_unit_rows", "relationship_rows", "post_rows", "member_rows"))
                    .as("nothing is read from A8 once the re-check has failed").isEqualTo(readsBefore);
            assertThatThrownBy(() -> a8.findStaffByLoginName(LOGIN_NAME)).as("the stopped adapter answers 'unavailable'")
                    .isInstanceOf(ExternalSystemUnavailableException.class);
            assertThat(baseTableReads("a8world", "org_unit_rows", "relationship_rows", "post_rows", "member_rows")).isEqualTo(readsBefore);
        }
    }

    @Test
    @DisplayName("ADR37-1 运行期重检：启动后部门简称账号多了一项写权限，下一次简称刷新开始前重检失败，适配器停止并写 ALERT，不再读取")
    void writeGrantedAfterStartupStopsTheShortNameAdapterBeforeTheNextRefresh(CapturedOutput output) throws SQLException {
        Account account = baselineAccount(ReadOnlyConnection.LEGACY_NAMES);
        try (ConfigurableApplicationContext context = start(Map.of(ReadOnlyConnection.LEGACY_NAMES, account))) {
            OrgShortNames names = context.getBean(OrgShortNames.class);
            assertThat(names.readShortNames()).as("the adapter reads before the grant changes").hasSize(1);

            grantWriteBehindTheDatabaseDeny(account);
            long readsBefore = baseTableReads("legacyworld", "short_name_rows");
            double failedBefore = shortNameRefreshFailures(context);
            context.getBean(RefreshShortNames.class).execute();

            assertThat(shortNameRefreshFailures(context)).as("the refresh is counted as failed").isEqualTo(failedBefore + 1);
            assertThat(output.getAll()).as("the failed re-check raises an ALERT naming item (b)").containsPattern("ALERT[^\\n]*[(（]b[)）]");
            assertThat(baseTableReads("legacyworld", "short_name_rows")).as("nothing is read from the old system once the re-check has failed")
                    .isEqualTo(readsBefore);
            assertThatThrownBy(names::readShortNames).isInstanceOf(ExternalSystemUnavailableException.class);
            assertThat(baseTableReads("legacyworld", "short_name_rows")).isEqualTo(readsBefore);
        }
    }

    @Test
    @DisplayName("ADR37-1 老系统待办连接在启动校验时遇数据库异常不吞：库不可达即拒绝启动（ADR-0037 第 4 项第 2 条）")
    void legacyInboxCheckDoesNotSwallowADatabaseFailure() {
        Account account = baselineAccount(ReadOnlyConnection.LEGACY_APPROVAL);

        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext context = start(Map.of(ReadOnlyConnection.LEGACY_APPROVAL, account), "no_such_database")) {
                assertThat(context.isActive()).isTrue();
            }
        });

        assertThat(failure).as("the legacy inbox check could not reach its database: it must refuse, not log and carry on").isNotNull();
    }

    static double shortNameRefreshFailures(ConfigurableApplicationContext context) {
        Counter failures = context.getBean(MeterRegistry.class).find("forcitis.organization.workforce.short.name.refresh.failures").counter();
        return failures == null ? 0 : failures.count();
    }

    static void grantWriteBehindTheDatabaseDeny(Account account) throws SQLException {
        run(account.connection().database, "REVOKE INSERT FROM " + q(account.login()), "GRANT INSERT ON SCHEMA::dbo TO " + q(account.login()));
    }

    static SyncResult runSync(SyncOrgSnapshot sync) {
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.test")) {
            return sync.execute(SyncTrigger.MANUAL, false);
        }
    }

    static ConfigurableApplicationContext start(Map<ReadOnlyConnection, Account> enabled) {
        return start(enabled, c -> url(c.database));
    }

    static ConfigurableApplicationContext start(Map<ReadOnlyConnection, Account> enabled, String approvalDatabaseOverride) {
        return start(enabled, c -> url(c == ReadOnlyConnection.LEGACY_APPROVAL ? approvalDatabaseOverride : c.database));
    }

    /** Command-line arguments: they rank above application.properties, which switches every external connection off by default. */
    static ConfigurableApplicationContext start(Map<ReadOnlyConnection, Account> enabled, Function<ReadOnlyConnection, String> urlOf) {
        List<String> args = new ArrayList<>(List.of(
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--flowable.async-executor-activate=false",
                "--forcitis.platform.approval.deadline.enabled=false",
                "--forcitis.platform.integration.seeyon.views.enabled=" + enabled.containsKey(ReadOnlyConnection.A8),
                "--forcitis.platform.integration.legacy.enabled=" + enabled.containsKey(ReadOnlyConnection.LEGACY_NAMES)));
        Account a8 = enabled.get(ReadOnlyConnection.A8);
        if (a8 != null) {
            String views = "--forcitis.platform.integration.seeyon.views.";
            args.addAll(List.of(views + "jdbc-url=" + urlOf.apply(ReadOnlyConnection.A8), views + "username=" + a8.login(), views + "password=" + a8.password(),
                    views + "head-relationship-type-id=" + HEAD_TYPE, views + "regional-receivables-manager-relationship-type-id=" + MANAGER_TYPE,
                    views + "delivery-unit-yes-enum-id=9001", views + "fee-earner-yes-enum-id=9002", views + "root-path=00000001"));
        }
        Account names = enabled.get(ReadOnlyConnection.LEGACY_NAMES);
        if (names != null) {
            String legacy = "--forcitis.platform.integration.legacy.";
            args.addAll(List.of(legacy + "jdbc-url=" + urlOf.apply(ReadOnlyConnection.LEGACY_NAMES), legacy + "username=" + names.login(), legacy + "password=" + names.password()));
        }
        Account approval = enabled.get(ReadOnlyConnection.LEGACY_APPROVAL);
        String inbox = "--forcitis.platform.approval.legacy.";
        if (approval != null) {
            args.addAll(List.of(inbox + "url=" + urlOf.apply(ReadOnlyConnection.LEGACY_APPROVAL), inbox + "username=" + approval.login(), inbox + "password=" + approval.password(),
                    inbox + "view-name=dbo.v_psa_one_legacy_todo"));
        } else {
            args.add(inbox + "url=");
        }
        return new SpringApplicationBuilder(OneApplication.class).run(args.toArray(String[]::new));
    }

    static String messages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
            for (Throwable suppressed : t.getSuppressed()) {
                all.append("  suppressed ").append(suppressed).append('\n');
            }
        }
        return all.toString();
    }

    /** A fresh login per case: no case can leak a grant into another. */
    static Account baselineAccount(ReadOnlyConnection connection) {
        String login = connection.loginPrefix + "_" + LOGINS.incrementAndGet();
        String password = "Ro1!" + UUID.randomUUID();
        try {
            if (connection == ReadOnlyConnection.A8) {
                ReadOnlyBaselineFixture.createViewSchemaReader(ReadOnlyAccountCheckIT::run, connection.database, login, password);
            } else {
                String view = connection == ReadOnlyConnection.LEGACY_APPROVAL ? "dbo.v_psa_one_legacy_todo" : "dbo.v_one_legacy_org_short_name";
                ReadOnlyBaselineFixture.createSingleViewReader(ReadOnlyAccountCheckIT::run, connection.database, view, login, password);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("cannot create the read-only login " + login, e);
        }
        return new Account(connection, login, password);
    }

    static void assertMatchesTheRdsBaseline(Account account) throws SQLException {
        ReadOnlyBaselineFixture.assertMatchesTheRdsBaseline(url(account.connection().database), account.connection().database, account.login(),
                account.password());
    }

    /** Index usage counts every seek, scan and lookup a query makes on the table, also through a view. */
    static long baseTableReads(String database, String... tables) throws SQLException {
        StringBuilder ids = new StringBuilder();
        for (String table : tables) {
            ids.append(ids.isEmpty() ? "" : ", ").append("OBJECT_ID(N'").append(database).append(".dbo.").append(table).append("')");
        }
        try (Connection c = admin("master"); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT ISNULL(SUM(user_seeks + user_scans + user_lookups), 0) FROM sys.dm_db_index_usage_stats "
                        + "WHERE database_id = DB_ID(N'" + database + "') AND object_id IN (" + ids + ")")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static String q(String name) {
        return ReadOnlyBaselineFixture.q(name);
    }

    static String url(String database) {
        return "jdbc:sqlserver://" + MSSQL.getHost() + ":" + MSSQL.getMappedPort(1433) + ";databaseName=" + database
                + ";encrypt=true;trustServerCertificate=true";
    }

    static Connection admin(String database) throws SQLException {
        return DriverManager.getConnection(url(database) + ";loginTimeout=30", "sa", SA_PASSWORD);
    }

    static void run(String database, String... statements) throws SQLException {
        try (Connection c = admin(database); Statement s = c.createStatement()) {
            for (String sql : statements) {
                s.execute(sql);
            }
        }
    }

    private static void createExternalWorld() {
        try {
            waitForAdministratorLogin();
            run("master", "CREATE DATABASE a8world", "CREATE DATABASE legacyworld", "CREATE DATABASE otherworld");
            ReadOnlyBaselineFixture.createRdsPlatform(ReadOnlyAccountCheckIT::run);
            createA8World();
            createLegacyWorld();
        } catch (SQLException | ConditionTimeoutException e) {
            throw new IllegalStateException("cannot prepare the external databases; SQL Server log:\n" + MSSQL.getLogs(), e);
        }
    }

    /** Polls in the calling thread: this runs inside the static initializer. */
    private static void waitForAdministratorLogin() {
        Awaitility.await("SQL Server accepts the administrator login")
                .atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500)).pollInSameThread().ignoreExceptions().until(() -> {
                    admin("master").close();
                    return true;
                });
    }

    private static void createA8World() throws SQLException {
        run("a8world", "CREATE SCHEMA " + ReadOnlyBaselineFixture.VIEW_SCHEMA + " AUTHORIZATION dbo",
                "CREATE TABLE dbo.org_unit_rows (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, PATH nvarchar(200), IS_DELETED bit, "
                        + "IS_INTERNAL bit, IS_ENABLE bit, CATEGORY nvarchar(16), DELIVERY_FLAG_ENUM_ID nvarchar(32))",
                "CREATE TABLE dbo.relationship_rows (SOURCE_MEMBER_ID bigint, ORG_UNIT_ID bigint, RELATIONSHIP_TYPE_ID bigint, MEMBER_IS_DELETED bit)",
                "CREATE TABLE dbo.post_rows (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, STATUS int, IS_DELETED bit)",
                "CREATE TABLE dbo.member_rows (ID bigint PRIMARY KEY, NAME nvarchar(64), CODE nvarchar(32), ORG_UNIT_ID bigint, ORG_UNIT_PATH nvarchar(200), "
                        + "POST_ID bigint, SUPERVISOR_MEMBER_ID bigint, WE_COM_ID nvarchar(64), IS_INTERNAL bit, LOGIN_NAME nvarchar(64), "
                        + "ENTERPRISE_EMAIL nvarchar(128), GENDER nvarchar(8), FEE_EARNER_ENUM_ID nvarchar(32))",
                "INSERT dbo.org_unit_rows VALUES (1, 'C1', N'Root', 1, '00000001', 0, 1, 1, N'总部', NULL), (2, 'C2', N'Delivery', 2, '000000010001', 0, 1, 1, N'区域', '9001')",
                "INSERT dbo.post_rows VALUES (100, 'P100', N'Consultant', 1, 0, 0)",
                "INSERT dbo.member_rows VALUES (8001, N'Head', 'E8001', 2, '000000010001', 100, NULL, 'wx-8001', 1, '" + LOGIN_NAME + "', 'head@example.test', N'女', '9002')",
                "INSERT dbo.relationship_rows VALUES (8001, 2, " + HEAD_TYPE + ", 0), (8001, 2, " + MANAGER_TYPE + ", 0)",
                "CREATE VIEW forcitis_one.v_org_unit AS SELECT ID, CODE, NAME, SORT_ID, PATH, IS_DELETED, IS_INTERNAL, IS_ENABLE, CATEGORY, DELIVERY_FLAG_ENUM_ID FROM dbo.org_unit_rows",
                "CREATE VIEW forcitis_one.v_org_relationship AS SELECT SOURCE_MEMBER_ID, ORG_UNIT_ID, RELATIONSHIP_TYPE_ID, MEMBER_IS_DELETED FROM dbo.relationship_rows",
                "CREATE VIEW forcitis_one.v_org_post AS SELECT ID, CODE, NAME, SORT_ID, STATUS, IS_DELETED FROM dbo.post_rows",
                "CREATE VIEW forcitis_one.v_org_member AS SELECT ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, SUPERVISOR_MEMBER_ID, WE_COM_ID, IS_INTERNAL, "
                        + "LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID FROM dbo.member_rows");
    }

    private static void createLegacyWorld() throws SQLException {
        run("legacyworld",
                "CREATE TABLE dbo.todo_rows (assignee_user_id bigint, task_id nvarchar(64), process_instance_id nvarchar(64), task_def_key nvarchar(64), "
                        + "task_name nvarchar(128), process_title nvarchar(256), created_at datetime2)",
                "CREATE TABLE dbo.short_name_rows (id bigint, short_name nvarchar(64))",
                "INSERT dbo.todo_rows VALUES (1001, 'T-1', 'I-1', 'approve', N'Approve', N'Legacy item', '2026-10-01T08:00:00')",
                "INSERT dbo.short_name_rows VALUES (2, N'Delivery')",
                "CREATE VIEW dbo.v_psa_one_legacy_todo AS SELECT assignee_user_id, task_id, process_instance_id, task_def_key, task_name, process_title, created_at FROM dbo.todo_rows",
                "CREATE VIEW dbo.v_one_legacy_org_short_name AS SELECT id, short_name FROM dbo.short_name_rows");
    }
}
