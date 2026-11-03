package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.OneApplication;
import com.forcitis.one.TestMssql;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.application.RefreshShortNames;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR37-1 (ADR-0037 item 4, implementation card of one-coordinator and one-ops 2026-10-15): every read-only external connection is checked
 * against the RDS platform baseline when the application starts and again before each short-name refresh; anything more, anything less or
 * anything that cannot be determined refuses. The application is started for real (black box) against a real SQL Server, one start per case.
 * The re-check before an organisation sync run went with the sync in #934 (ADR-0055 section 6).
 *
 * <p>The logins come from {@link ReadOnlyBaselineFixture}; {@link #fixtureReproducesTheRdsBaseline} proves they equal the RDS export
 * before anything else is believed.
 */
@ExtendWith(OutputCaptureExtension.class)
class ReadOnlyAccountCheckIT {

    static final String LOGIN_NAME = "13900008001";
    static final long HEAD_TYPE = 7001;
    static final long MANAGER_TYPE = 7002;

    static final String SA_PASSWORD = TestMssql.SA_PASSWORD;
    static final AtomicInteger LOGINS = new AtomicInteger();

    static final PostgreSQLContainer POSTGRES = TestPostgres.container()
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static final GenericContainer<?> MSSQL = TestMssql.shared();

    static final String A8_WORLD = TestMssql.database(ReadOnlyAccountCheckIT.class, "a8world").name();
    static final String LEGACY_WORLD = TestMssql.database(ReadOnlyAccountCheckIT.class, "legacyworld").name();
    static final String OTHER_WORLD = TestMssql.database(ReadOnlyAccountCheckIT.class, "otherworld").name();

    static {
        POSTGRES.start();
        createExternalWorld();
    }

    enum ReadOnlyConnection {
        A8(A8_WORLD, "a8_ro", ReadOnlyBaselineFixture.VIEW_SCHEMA, "dbo.ORG_MEMBER"),
        LEGACY_NAMES(LEGACY_WORLD, "names_ro", "dbo", "dbo.short_name_rows");

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
                    new Sql(OTHER_WORLD, "CREATE USER " + q(a.login()) + " FOR LOGIN " + q(a.login())))),
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
            // Was GRANT UPDATE: the login's own database-level DENY UPDATE overrides that one now (one-coordinator 2026-10-15 C-14 定位裁定, see overriddenPublicGrantsDoNotRefuseStartup).
            new Violation("复审 H-1：public 角色在用户对象上的授权 GRANT CONTROL ON 可读底表 TO public（库级 DENY 清单不含 CONTROL）", "g", a -> List.of(
                    new Sql(a.connection().database, "GRANT CONTROL ON " + a.connection().baseTable + " TO public")), a -> List.of(
                    new Sql(a.connection().database, "REVOKE CONTROL ON " + a.connection().baseTable + " FROM public"))),
            new Violation("C-14 定位裁定安全条件 2：public 角色在用户对象上的授权 GRANT TAKE OWNERSHIP ON 可读底表 TO public", "g", a -> List.of(
                    new Sql(a.connection().database, "GRANT TAKE OWNERSHIP ON " + a.connection().baseTable + " TO public")), a -> List.of(
                    new Sql(a.connection().database, "REVOKE TAKE OWNERSHIP ON " + a.connection().baseTable + " FROM public"))),
            // Was CREATE USER in msdb plus db_owner: the fixture now gives every login the own msdb user RDS gives it (user 2026-10-15 「纳入基线」), so only the extra role is added.
            new Violation("复审 H-2、C-14 (f) 纳入基线：msdb 本人用户（三个 SQLAgent 角色，同 RDS）另加 db_owner", "f", a -> List.of(
                    new Sql("msdb", "ALTER ROLE db_owner ADD MEMBER " + q(a.login())))),
            new Violation("复审 H-2：在 master 有该登录自己的用户（无角色；系统库只能经 guest 进入）", "f", a -> List.of(
                    new Sql("master", "CREATE USER " + q(a.login()) + " FOR LOGIN " + q(a.login())))),
            new Violation("C-14 (f) 纳入基线：msdb 本人用户多一个三个 SQLAgent 角色以外的角色 DatabaseMailUserRole", "f", a -> List.of(
                    new Sql("msdb", "ALTER ROLE DatabaseMailUserRole ADD MEMBER " + q(a.login())))),
            new Violation("C-14 (f) 纳入基线：rdscore 本人用户加入一个角色 db_datareader（只允许无角色、库级只有 CONNECT）", "f", a -> List.of(
                    new Sql("rdscore", "ALTER ROLE db_datareader ADD MEMBER " + q(a.login())))),
            new Violation("C-14 (f) 纳入基线附加条件：本账号拥有一个 Agent 作业（msdb 作业 owner 为本登录）", "f", a -> List.of(
                    new Sql("msdb", addJob(a.login() + "_job", a.login()))), a -> List.of(
                    new Sql("msdb", deleteJob(a.login() + "_job")))));

    /** C-14 (f) review H-1: what the own msdb user may hold beyond the baseline; the three global ones change msdb for all logins and undo it in finally. */
    static final List<Violation> MSDB_VIOLATIONS = ReadOnlyBaselineFixture.MSDB_COUNTEREXAMPLES.stream().map(x -> new Violation(
            "复审 H-1：msdb 本人用户 " + x.name(), "f", a -> msdb(x.apply().apply(q(a.login()))), a -> msdb(x.undo().apply(q(a.login()))))).toList();

    static List<Sql> msdb(List<String> statements) {
        return statements.stream().map(statement -> new Sql("msdb", statement)).toList();
    }

    static Stream<Arguments> everyViolationOnEveryConnection() {
        return Stream.concat(VIOLATIONS.stream(), MSDB_VIOLATIONS.stream()).flatMap(v -> Stream.of(ReadOnlyConnection.values()).map(c -> Arguments.of(c, v)));
    }

    @Test
    @DisplayName("ADR37-1 夹具：容器里新建的三类只读登录与 RDS 实测基线逐项相等（服务器级、库级、角色、可访问的库、库级 DENY 与 GRANT；C-14 (f) 纳入基线：msdb 本人用户恰在三个 SQLAgent 角色、rdscore 本人用户无角色只有 CONNECT、master 与 tempdb 无本人用户、不拥有 Agent 作业）")
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("msdbCounterexamples")
    @DisplayName("C-14 (f) 复审 H-1 夹具：每个 msdb 反例确实让本人用户在 msdb 内的有效权限为真（或直接授权越线），撤销后回到基线（基线账号所有新检查为假）")
    void msdbCounterexampleReallyHoldsItsPrivilegeAndUndoRestoresTheBaseline(ReadOnlyBaselineFixture.MsdbCounterexample counterexample) throws SQLException {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        assertMatchesTheRdsBaseline(account);
        String user = q(account.login());
        List<String> held;
        try {
            for (Sql sql : msdb(counterexample.apply().apply(user))) {
                run(sql.database(), sql.statement());
            }
            held = ReadOnlyBaselineFixture.ownMsdbPrivileges(url(A8_WORLD), account.login(), account.password());
        } finally {
            for (Sql sql : msdb(counterexample.undo().apply(user))) {
                run(sql.database(), sql.statement());
            }
        }

        assertThat(held).as("%s: what the login holds in msdb while the counterexample stands", counterexample)
                .containsExactlyInAnyOrderElementsOf(counterexample.expected());
        assertMatchesTheRdsBaseline(account);
    }

    static Stream<Arguments> msdbCounterexamples() {
        return ReadOnlyBaselineFixture.MSDB_COUNTEREXAMPLES.stream().map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("C-14 (f) 复审 H-1 不误判（one-ops 样本形状）：msdb 本人用户除 CONNECT 与三个 SQLAgent 角色外，直接有对象级 GRANT SELECT、GRANT EXECUTE 与 DENY SELECT 时应用仍正常启动，且夹具核对这些授权都在允许形状内")
    void oneOpsShapedMsdbGrantsDoNotRefuseStartup(ReadOnlyConnection connection) throws SQLException {
        Account account = baselineAccount(connection);
        run("msdb", ReadOnlyBaselineFixture.oneOpsSampleMsdbGrants(q(account.login())).toArray(String[]::new));
        assertThat(ReadOnlyBaselineFixture.ownMsdbPrivileges(url(connection.database), account.login(), account.password()))
                .as("the sample shape is inside the allowed shape").isEmpty();

        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                assertThat(context.isActive()).isTrue();
            }
        });

        assertThat(failure).as("an account with the one-ops direct msdb grants must pass; refused with:\n%s", messages(failure)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("C-14 (f) 复审 H-1' 不误判（默认授权）：令牌范围扫描遇到 SQL Server 默认授给 public 的 VIEW ANY COLUMN MASTER KEY / ENCRYPTION KEY DEFINITION 与 public、SQLAgent 角色的默认对象级 GRANT SELECT / EXECUTE 时应用仍正常启动（夹具先核实这些默认授权确实存在、账号令牌范围无越线）")
    void msdbDefaultGrantsOfPublicAndTheAgentRolesDoNotRefuseStartup(ReadOnlyConnection connection) throws SQLException {
        try (Connection c = admin("msdb"); Statement s = c.createStatement()) {
            ReadOnlyBaselineFixture.assertMsdbDefaultGrantsExist(s);
        }
        Account account = baselineAccount(connection);
        assertThat(ReadOnlyBaselineFixture.msdbToken(url(connection.database), account.login(), account.password()))
                .as("the token holds the three roles and public beside the own user").contains("public", "SQLAgentUserRole", "SQLAgentReaderRole", "SQLAgentOperatorRole");
        assertThat(ReadOnlyBaselineFixture.ownMsdbPrivileges(url(connection.database), account.login(), account.password()))
                .as("the fixture: the defaults are inside the allow-list").isEmpty();

        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                assertThat(context.isActive()).isTrue();
            }
        });

        assertThat(failure).as("the platform defaults in msdb must pass; refused with:\n%s", messages(failure)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("C-14 (f) 复审 H-1' 只经 guest 进 msdb：账号在 msdb 没有本人用户、guest 仍启用时，授给 guest 的 ALTER ANY SCHEMA 使应用拒绝启动并报出 (f)（现行实现对「msdb 无本人用户」不查权限）")
    void alterAnySchemaGrantedToGuestRefusesStartupWhenMsdbIsReachedThroughGuestOnly(ReadOnlyConnection connection) throws SQLException {
        Account account = guestOnlyAccount(connection);
        Throwable failure;
        try {
            run("msdb", ReadOnlyBaselineFixture.GUEST_READS_AGENT_JOBS, "GRANT ALTER ANY SCHEMA TO guest");
            assertThat(ReadOnlyBaselineFixture.ownMsdbPrivileges(url(connection.database), account.login(), account.password()))
                    .as("the fixture: guest's grant reaches the login that has no own user in msdb")
                    .containsExactlyInAnyOrder("EFFECTIVE ALTER ANY SCHEMA", "TOKEN guest DATABASE G ALTER ANY SCHEMA");
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            run("msdb", "REVOKE ALTER ANY SCHEMA FROM guest", ReadOnlyBaselineFixture.GUEST_READS_AGENT_JOBS_UNDO);
        }

        assertThat(failure).as("%s: the application started anyway", connection).isNotNull();
        assertThat(messages(failure)).as("the refusal names item (f)").containsPattern("[(（]f[)）]");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("C-14 (f) 复审 H-A 案例 A2 只经 guest 进 msdb：DatabaseMailUserRole 嵌入 guest（角色能力全是对象级 EXECUTE，令牌授权扫描与六项都看不到）时，令牌里出现 public 以外的角色，应用拒绝启动并报出 (f)")
    void aRoleNestedIntoGuestRefusesStartupWhenMsdbIsReachedThroughGuestOnly(ReadOnlyConnection connection) throws SQLException {
        Account account = guestOnlyAccount(connection);
        Throwable failure;
        try {
            run("msdb", ReadOnlyBaselineFixture.GUEST_READS_AGENT_JOBS, "ALTER ROLE DatabaseMailUserRole ADD MEMBER guest");
            assertThat(ReadOnlyBaselineFixture.ownMsdbPrivileges(url(connection.database), account.login(), account.password()))
                    .as("the fixture: the role reaches the login that has no own user in msdb, and nothing else shows it")
                    .containsExactly("TOKEN_ROLE DatabaseMailUserRole");
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            run("msdb", "ALTER ROLE DatabaseMailUserRole DROP MEMBER guest", ReadOnlyBaselineFixture.GUEST_READS_AGENT_JOBS_UNDO);
        }

        assertThat(failure).as("%s: the application started anyway", connection).isNotNull();
        assertThat(messages(failure)).as("the refusal names item (f)").containsPattern("[(（]f[)）]");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("C-14 (f) 复审 H-A / M-A 不误判（默认）：guest 拥有 schema guest 是平台默认，只经 guest 进 msdb 的账号（夹具先核实 guest 确实拥有它）仍正常启动")
    void guestOwningItsOwnSchemaDoesNotRefuseStartup(ReadOnlyConnection connection) throws SQLException {
        try (Connection c = admin("msdb"); Statement s = c.createStatement()) {
            ReadOnlyBaselineFixture.assertPlatformSchemasAreOwnedByTheirNamesakes(s);
        }
        Account account = guestOnlyAccount(connection);
        Throwable failure;
        try {
            run("msdb", ReadOnlyBaselineFixture.GUEST_READS_AGENT_JOBS);
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            run("msdb", ReadOnlyBaselineFixture.GUEST_READS_AGENT_JOBS_UNDO);
        }

        assertThat(failure).as("guest's own schema is a platform default; refused with:\n%s", messages(failure)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("C-14 (f) 复审 H-1' 只经 guest 进 msdb 的对照：guest 只有默认授权（另让 guest 能读自己的 Agent 作业数，否则 (f) 因读不到作业判定不了）时应用正常启动（msdb 可进即检查令牌范围，没有越线就通过）")
    void guestOnlyAccountWithTheDefaultGuestGrantsStarts(ReadOnlyConnection connection) throws SQLException {
        Account account = guestOnlyAccount(connection);
        Throwable failure;
        try {
            run("msdb", ReadOnlyBaselineFixture.GUEST_READS_AGENT_JOBS);
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            run("msdb", ReadOnlyBaselineFixture.GUEST_READS_AGENT_JOBS_UNDO);
        }

        assertThat(failure).as("a login that reaches msdb through guest alone, with nothing beyond the defaults, passes; refused with:\n%s", messages(failure)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("C-14 (f) 复审 H-1' 不误判（guest 不在令牌）：账号在 msdb 有本人用户时，授给 guest 的 ALTER ANY SCHEMA 到不了它（令牌里没有 guest），应用正常启动")
    void aGrantToGuestDoesNotRefuseAnAccountWithItsOwnMsdbUser(ReadOnlyConnection connection) throws SQLException {
        Account account = baselineAccount(connection);
        Throwable failure;
        try {
            run("msdb", "GRANT ALTER ANY SCHEMA TO guest");
            assertThat(ReadOnlyBaselineFixture.ownMsdbPrivileges(url(connection.database), account.login(), account.password()))
                    .as("the fixture: guest's grant does not reach a login with its own msdb user").isEmpty();
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            run("msdb", "REVOKE ALTER ANY SCHEMA FROM guest");
        }

        assertThat(failure).as("refused with:\n%s", messages(failure)).isNull();
    }

    /** A baseline account whose own msdb user is dropped: msdb stays reachable through the enabled guest (the fixture checks both), with no role of its own. */
    static Account guestOnlyAccount(ReadOnlyConnection connection) throws SQLException {
        try (Connection c = admin("msdb"); Statement s = c.createStatement()) {
            ReadOnlyBaselineFixture.assertPlatformSchemasAreOwnedByTheirNamesakes(s);
        }
        Account account = baselineAccount(connection);
        ReadOnlyBaselineFixture.dropMsdbOwnUser(ReadOnlyAccountCheckIT::run, q(account.login()));
        assertThat(ReadOnlyBaselineFixture.msdbToken(url(connection.database), account.login(), account.password()))
                .as("the fixture: msdb is reached through guest alone").containsExactly("guest", "public");
        assertThat(ReadOnlyBaselineFixture.ownMsdbPrivileges(url(connection.database), account.login(), account.password()))
                .as("the fixture: nothing beyond the defaults for guest and public").isEmpty();
        return account;
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
    @DisplayName("ADR-0037 第 4 项 (b) 收窄 RULING-ADR37-10（one-coordinator 2026-10-15 C-14 定位裁定，安全判定放宽）：public 的对象级 GRANT UPDATE 与 GRANT EXECUTE 被本账号库级同名 DENY 覆盖时不拒绝启动")
    void overriddenPublicGrantsDoNotRefuseStartup(ReadOnlyConnection connection) throws SQLException {
        Account account = baselineAccount(connection);
        String procedure = "dbo." + q("p_" + marker());
        String database = connection.database;
        Throwable failure;
        try {
            run(database, "GRANT UPDATE ON " + connection.baseTable + " TO public", "CREATE PROCEDURE " + procedure + " AS SELECT 1",
                    "GRANT EXECUTE ON OBJECT::" + procedure + " TO public");
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(connection, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            run(database, "REVOKE UPDATE ON " + connection.baseTable + " FROM public", "DROP PROCEDURE IF EXISTS " + procedure);
        }

        assertThat(failure).as("grants the login's own database-level DENY of the same permission overrides; refused with:\n%s", messages(failure)).isNull();
    }

    @Test
    @DisplayName("C-14 定位裁定（one-1a-next-3 条件）：启动校验拒绝 public 的 GRANT CONTROL 时只报项 (g)，拒绝原因与日志中不出现 schema 名与对象名（定位只由只读核实命令输出）")
    void startupRefusalNamesNoSchemaOrObject(CapturedOutput output) throws SQLException {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        String mark = marker();
        String table = q("s_" + mark) + "." + q("t_" + mark);
        Throwable failure;
        try {
            run(A8_WORLD, "CREATE SCHEMA " + q("s_" + mark) + " AUTHORIZATION dbo", "CREATE TABLE " + table + " (id bigint)",
                    "GRANT CONTROL ON OBJECT::" + table + " TO public");
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(ReadOnlyConnection.A8, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            run(A8_WORLD, "DROP TABLE IF EXISTS " + table, "DROP SCHEMA IF EXISTS " + q("s_" + mark));
        }

        assertThat(failure).as("public holds CONTROL on a table: the application started anyway").isNotNull();
        assertThat(messages(failure)).as("the refusal names item (g)").containsPattern("[(（]g[)）]");
        assertThat(messages(failure)).as("no schema or object name in the refusal").doesNotContain(mark);
        assertThat(output.getAll()).as("no schema or object name in the log").doesNotContain(mark);
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线附加条件（one-coordinator 2026-10-15）：本账号拥有一个 Agent 作业时拒绝启动并报项 (f)，拒绝原因与日志中不出现作业名")
    void ownedAgentJobRefusesStartupWithoutTheJobName(CapturedOutput output) throws SQLException {
        Account account = baselineAccount(ReadOnlyConnection.LEGACY_NAMES);
        String job = "j_" + marker();
        Throwable failure;
        try {
            run("msdb", addJob(job, account.login()));
            failure = catchThrowable(() -> {
                try (ConfigurableApplicationContext context = start(Map.of(ReadOnlyConnection.LEGACY_NAMES, account))) {
                    assertThat(context.isActive()).isTrue();
                }
            });
        } finally {
            run("msdb", deleteJob(job));
        }

        assertThat(failure).as("the login owns an Agent job: the application started anyway").isNotNull();
        assertThat(messages(failure)).as("the refusal names item (f)").containsPattern("[(（]f[)）]");
        assertThat(messages(failure)).as("no job name in the refusal").doesNotContain(job);
        assertThat(output.getAll()).as("no job name in the log").doesNotContain(job);
    }

    /** sa may name any login as owner; the Agent need not run for msdb to record the job (SQL Server 2022 container, checked 2026-10-15). */
    static String addJob(String job, String ownerLogin) {
        return "EXEC msdb.dbo.sp_add_job @job_name = N'" + job + "', @owner_login_name = N'" + ownerLogin + "'";
    }

    static String deleteJob(String job) {
        return "IF EXISTS (SELECT 1 FROM msdb.dbo.sysjobs WHERE name = N'" + job + "') EXEC msdb.dbo.sp_delete_job @job_name = N'" + job + "'";
    }

    static String marker() {
        return "mk" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
    @DisplayName("ADR37-1 运行期重检：启动后部门简称账号多了一项写权限，下一次简称刷新开始前重检失败，适配器停止并写 ALERT，不再读取")
    void writeGrantedAfterStartupStopsTheShortNameAdapterBeforeTheNextRefresh(CapturedOutput output) throws SQLException {
        Account account = baselineAccount(ReadOnlyConnection.LEGACY_NAMES);
        try (ConfigurableApplicationContext context = start(Map.of(ReadOnlyConnection.LEGACY_NAMES, account))) {
            OrgShortNames names = context.getBean(OrgShortNames.class);
            legacyReferenceOfTheShortNameUnit(context);
            assertThat(names.readShortNames()).as("the adapter reads before the grant changes").hasSize(1);

            grantWriteBehindTheDatabaseDeny(account);
            long readsBefore = baseTableReads(LEGACY_WORLD, "short_name_rows");
            double failedBefore = shortNameRefreshFailures(context);
            context.getBean(RefreshShortNames.class).execute();

            assertThat(shortNameRefreshFailures(context)).as("the refresh is counted as failed").isEqualTo(failedBefore + 1);
            assertThat(output.getAll()).as("the failed re-check raises an ALERT naming item (b)").containsPattern("ALERT[^\\n]*[(（]b[)）]");
            assertThat(baseTableReads(LEGACY_WORLD, "short_name_rows")).as("nothing is read from the old system once the re-check has failed")
                    .isEqualTo(readsBefore);
            assertThatThrownBy(names::readShortNames).isInstanceOf(ExternalSystemUnavailableException.class);
            assertThat(baseTableReads(LEGACY_WORLD, "short_name_rows")).isEqualTo(readsBefore);
        }
    }

    /**
     * R-1b P3b (C8, RULING-P3B-3): once the short names are handed out by own id, the old-system department 2 of the fixture needs its
     * (LEGACY_SYSTEM, ORG_UNIT) reference to be handed out at all; before P3b (EmployeeId still a number) nothing is added.
     */
    static void legacyReferenceOfTheShortNameUnit(ConfigurableApplicationContext context) {
        if (com.forcitis.one.kernel.EmployeeId.class.getRecordComponents()[0].getType() != UUID.class) {
            return;
        }
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            SourceReferencesProbe.of(context).allocate("LEGACY_SYSTEM", "ORG_UNIT", 2);
        }
    }

    static double shortNameRefreshFailures(ConfigurableApplicationContext context) {
        Counter failures = context.getBean(MeterRegistry.class).find("forcitis.organization.workforce.short.name.refresh.failures").counter();
        return failures == null ? 0 : failures.count();
    }

    static void grantWriteBehindTheDatabaseDeny(Account account) throws SQLException {
        run(account.connection().database, "REVOKE INSERT FROM " + q(account.login()), "GRANT INSERT ON SCHEMA::dbo TO " + q(account.login()));
    }

    static ConfigurableApplicationContext start(Map<ReadOnlyConnection, Account> enabled) {
        return start(enabled, c -> url(c.database), List.of());
    }

    /** Command-line arguments: they rank above application.properties, which switches every external connection off by default. */
    static ConfigurableApplicationContext start(Map<ReadOnlyConnection, Account> enabled, Function<ReadOnlyConnection, String> urlOf) {
        return start(enabled, urlOf, List.of());
    }

    static ConfigurableApplicationContext start(Map<ReadOnlyConnection, Account> enabled, Function<ReadOnlyConnection, String> urlOf, List<String> extraArguments) {
        List<String> args = new ArrayList<>(List.of(
                "--server.port=0",
                "--server.address=127.0.0.1",
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
        args.addAll(extraArguments);
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
        String login = TestMssql.login(ReadOnlyAccountCheckIT.class, connection.loginPrefix + "_" + LOGINS.incrementAndGet());
        String password = "Ro1!" + UUID.randomUUID();
        try {
            if (connection == ReadOnlyConnection.A8) {
                ReadOnlyBaselineFixture.createViewSchemaReader(ReadOnlyAccountCheckIT::run, connection.database, login, password);
            } else {
                ReadOnlyBaselineFixture.createSingleViewReader(ReadOnlyAccountCheckIT::run, connection.database, "dbo.v_one_legacy_org_short_name", login, password);
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

    private static void createExternalWorld() {
        try {
            ReadOnlyBaselineFixture.createRdsPlatform(ReadOnlyAccountCheckIT::run);
            createA8World();
            createLegacyWorld();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot prepare the external databases; SQL Server log:\n" + MSSQL.getLogs(), e);
        }
    }

    private static void createA8World() throws SQLException {
        run(A8_WORLD, "CREATE SCHEMA " + ReadOnlyBaselineFixture.VIEW_SCHEMA + " AUTHORIZATION dbo",
                "CREATE TABLE dbo.ORG_UNIT (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, PATH nvarchar(200), IS_DELETED bit, "
                        + "IS_INTERNAL bit, IS_ENABLE bit, CATEGORY nvarchar(16), DELIVERY_FLAG_ENUM_ID nvarchar(32), TYPE nvarchar(32) NULL)",
                "CREATE TABLE dbo.relationship_rows (SOURCE_MEMBER_ID bigint, ORG_UNIT_ID bigint, RELATIONSHIP_TYPE_ID bigint, MEMBER_IS_DELETED bit)",
                "CREATE TABLE dbo.post_rows (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, STATUS int, IS_DELETED bit)",
                "CREATE TABLE dbo.ORG_MEMBER (ID bigint PRIMARY KEY, NAME nvarchar(64), CODE nvarchar(32), ORG_UNIT_ID bigint, ORG_UNIT_PATH nvarchar(200), "
                        + "POST_ID bigint, SUPERVISOR_MEMBER_ID bigint, WE_COM_ID nvarchar(64), IS_INTERNAL bit, LOGIN_NAME nvarchar(64), "
                        + "ENTERPRISE_EMAIL nvarchar(128), GENDER nvarchar(8), FEE_EARNER_ENUM_ID nvarchar(32), STATE smallint NULL, IS_DELETED smallint NULL, IS_ENABLE smallint NULL)",
                "INSERT dbo.ORG_UNIT VALUES (1, 'C1', N'Root', 1, '00000001', 0, 1, 1, N'总部', NULL, N'Account'), (2, 'C2', N'Delivery', 2, '000000010001', 0, 1, 1, N'区域', '9001', N'Department')",
                "INSERT dbo.post_rows VALUES (100, 'P100', N'Consultant', 1, 0, 0)",
                "INSERT dbo.ORG_MEMBER VALUES (8001, N'Head', 'E8001', 2, '000000010001', 100, NULL, 'wx-8001', 1, '" + LOGIN_NAME + "', 'head@example.test', N'女', '9002', 1, 0, 1)",
                "INSERT dbo.relationship_rows VALUES (8001, 2, " + HEAD_TYPE + ", 0), (8001, 2, " + MANAGER_TYPE + ", 0)",
                "CREATE VIEW forcitis_one.v_org_unit AS SELECT ID, CODE, NAME, SORT_ID, PATH, IS_DELETED, IS_INTERNAL, IS_ENABLE, CATEGORY, DELIVERY_FLAG_ENUM_ID FROM dbo.ORG_UNIT",
                "CREATE VIEW forcitis_one.v_org_relationship AS SELECT SOURCE_MEMBER_ID, ORG_UNIT_ID, RELATIONSHIP_TYPE_ID, MEMBER_IS_DELETED FROM dbo.relationship_rows",
                "CREATE VIEW forcitis_one.v_org_post AS SELECT ID, CODE, NAME, SORT_ID, STATUS, IS_DELETED FROM dbo.post_rows",
                "CREATE VIEW forcitis_one.v_org_member AS SELECT ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, SUPERVISOR_MEMBER_ID, WE_COM_ID, IS_INTERNAL, "
                        + "LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID FROM dbo.ORG_MEMBER");
    }

    private static void createLegacyWorld() throws SQLException {
        run(LEGACY_WORLD,
                "CREATE TABLE dbo.short_name_rows (id bigint, short_name nvarchar(64))",
                "INSERT dbo.short_name_rows VALUES (2, N'Delivery')",
                "CREATE VIEW dbo.v_one_legacy_org_short_name AS SELECT id, short_name FROM dbo.short_name_rows");
    }
}
