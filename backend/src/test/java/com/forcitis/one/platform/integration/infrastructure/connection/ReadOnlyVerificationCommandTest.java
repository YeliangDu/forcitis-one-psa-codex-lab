package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.platform.integration.infrastructure.connection.ExternalConnectionProperties.Limits;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyVerificationCommand.ReadOnlyConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.env.MockEnvironment;

/** Output and usage of the read-only verification command; the packaged command against SQL Server is acceptance.platform.integration.ReadOnlyVerificationCommandIT. */
class ReadOnlyVerificationCommandTest {

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();
    /** The process environment; every other property source of the environment stands for -D, the command line or SPRING_APPLICATION_JSON. */
    final Map<String, Object> processEnvironment = new HashMap<>();
    final MockEnvironment environment = new MockEnvironment() {
        @Override
        public Map<String, Object> getSystemEnvironment() {
            return processEnvironment;
        }
    };

    private int run(String... arguments) {
        return ReadOnlyVerificationCommand.run(arguments, environment, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
    }

    private static String closedSqlServerUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "jdbc:sqlserver://127.0.0.1:" + socket.getLocalPort() + ";databaseName=nowhere;encrypt=true;trustServerCertificate=true";
        }
    }

    private void shortBackgroundLimits() {
        environment.setProperty("forcitis.platform.integration.external-connection.background.acquire-timeout", "500ms");
        environment.setProperty("forcitis.platform.integration.external-connection.background.lock-timeout", "500ms");
        environment.setProperty("forcitis.platform.integration.external-connection.background.query-timeout", "1s");
        environment.setProperty("forcitis.platform.integration.external-connection.background.socket-timeout", "2s");
    }

    @Test
    @DisplayName("PAR-2 one line per item with reason codes and counts, the SQL error of a failed query, and the result; never the problem text with its names")
    void reportTellsCategoriesOnly() {
        FakeCatalog catalog = new FakeCatalog() {
            @Override
            public List<DatabaseLevelPermission> databaseLevelPermissions() {
                throw new CannotGetJdbcConnectionException("pool", new SQLTransientConnectionException("timed out", "S0001", 0,
                        new SQLException("denied on " + FakeCatalog.LEAKED, "S0001", 229)));
            }
        };
        catalog.roles.add("role_" + FakeCatalog.LEAKED);
        catalog.roles.add("db_datareader");
        catalog.reachableDatabases.add("otherworld");

        ReadOnlyVerificationCommand.report(ReadOnlyBaseline.check(catalog), new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).containsExactly(
                "(a) PASSED",
                "(b) UNDETERMINED reason=CATALOG_QUERY_FAILED count=1 error=229 sqlstate=S0001",
                "(c) VIOLATED reason=DATABASE_ROLE_MEMBERSHIP count=2",
                "(d) PASSED",
                "(e) PASSED",
                "(f) VIOLATED reason=OTHER_DATABASE_REACHABLE count=1",
                "  (f) violation reason=OTHER_DATABASE_REACHABLE database=\"otherworld\"",
                "(g) PASSED",
                "RESULT VIOLATED");
        assertThat(output()).doesNotContain(FakeCatalog.LEAKED).doesNotContain("db_datareader");
    }

    @Test
    @DisplayName("C-14 定位裁定（READONLY_VERIFY_ACCEPTANCE.md 第 3a 节）：定位行紧跟项行，字段顺序固定，名称加引号并最小转义，缺失写 -，本账号写 SELF，"
            + "先 violation 后 overridden 各按字典序，count 等于 violation 行数；项通过时也出 overridden 行")
    void locatorLines() {
        FakeCatalog catalog = new FakeCatalog();
        catalog.ownUser("tempdb");
        catalog.ownUser("master");
        catalog.reachableDatabases.add("other\"world");
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 42, "G", "CONTROL", true, 1L, "dbo", "t\n\u2028\u202E\\x",
                "public", "DATABASE_ROLE"));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 42, "G", "CONTROL", true, 1L, "dbo", "t\n\u2028\u202E\\x",
                "public", "DATABASE_ROLE"));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("SCHEMA", 5, "W", "TAKE OWNERSHIP", false, 5L, "报表", null, null, "SQL_USER"));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 43, "G", "EXECUTE", false, 1L, "dbo", "p_x", null, "SQL_USER"));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("TYPE", 44, "G", "REFERENCES", true, null, null, null, "public", "DATABASE_ROLE"));

        ReadOnlyVerificationCommand.report(ReadOnlyBaseline.check(catalog), new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).containsExactly(
                "(a) PASSED", "(b) PASSED", "(c) PASSED", "(d) PASSED", "(e) PASSED",
                "(f) VIOLATED reason=OTHER_DATABASE_REACHABLE count=1 reason=SYSTEM_DATABASE_OWN_USER count=2",
                "  (f) violation reason=OTHER_DATABASE_REACHABLE database=\"other\\\"world\"",
                "  (f) violation reason=SYSTEM_DATABASE_OWN_USER database=\"master\"",
                "  (f) violation reason=SYSTEM_DATABASE_OWN_USER database=\"tempdb\"",
                "  (f) ownUser database=\"master\" role=-",
                "  (f) ownUser database=\"tempdb\" role=-",
                "(g) VIOLATED reason=SECURABLE_GRANT_BEYOND_SELECT count=3",
                "  (g) violation reason=SECURABLE_GRANT_BEYOND_SELECT class=OBJECT_OR_COLUMN schema=\"dbo\" object=\"t\\n\\u2028\\u202E\\\\x\" objectId=42 "
                        + "permission=\"CONTROL\" state=GRANT grantee=\"public\" granteeType=DATABASE_ROLE",
                "  (g) violation reason=SECURABLE_GRANT_BEYOND_SELECT class=SCHEMA schema=\"报表\" object=- objectId=5 permission=\"TAKE OWNERSHIP\" "
                        + "state=GRANT_WITH_GRANT_OPTION grantee=SELF granteeType=SQL_USER",
                "  (g) violation reason=SECURABLE_GRANT_BEYOND_SELECT class=TYPE schema=- object=- objectId=44 permission=\"REFERENCES\" state=GRANT grantee=\"public\" granteeType=DATABASE_ROLE",
                "  (g) overridden class=OBJECT_OR_COLUMN schema=\"dbo\" object=\"p_x\" objectId=43 permission=\"EXECUTE\" state=GRANT grantee=SELF granteeType=SQL_USER by=DATABASE_DENY",
                "RESULT VIOLATED");
    }

    @Test
    @DisplayName("C-14 定位裁定：(g) 通过而有被拒绝覆盖的授权时，项行 PASSED 之后出 overridden 信息行，结果通过")
    void overriddenLinesUnderAPass() {
        FakeCatalog catalog = new FakeCatalog();
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 43, "G", "EXECUTE", true, 1L, "dbo", "p_x", "public", "DATABASE_ROLE"));

        ReadOnlyVerificationCommand.report(ReadOnlyBaseline.check(catalog), new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).containsExactly("(a) PASSED", "(b) PASSED", "(c) PASSED", "(d) PASSED", "(e) PASSED",
                "(f) PASSED", "(g) PASSED",
                "  (g) overridden class=OBJECT_OR_COLUMN schema=\"dbo\" object=\"p_x\" objectId=43 permission=\"EXECUTE\" state=GRANT grantee=\"public\" granteeType=DATABASE_ROLE by=DATABASE_DENY",
                "RESULT PASSED");
    }

    @Test
    @DisplayName("PAR-2 a pass prints seven PASSED lines and RESULT PASSED")
    void passReport() {
        ReadOnlyVerificationCommand.report(ReadOnlyBaseline.check(new FakeCatalog()), new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).containsExactly("(a) PASSED", "(b) PASSED", "(c) PASSED", "(d) PASSED", "(e) PASSED",
                "(f) PASSED", "(g) PASSED", "RESULT PASSED");
    }

    @Test
    @DisplayName("PAR-2 复审 M4 a driver error without SQLState prints sqlstate=none; count adds up the failed queries of an item")
    void missingSqlStateIsPrintedAsNone() {
        FakeCatalog catalog = new FakeCatalog();
        catalog.failure = new CannotGetJdbcConnectionException("pool", new SQLException("x"));

        ReadOnlyVerificationCommand.report(ReadOnlyBaseline.check(catalog), new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).containsExactly(
                "(a) UNDETERMINED reason=CATALOG_QUERY_FAILED count=1 error=0 sqlstate=none",
                "(b) UNDETERMINED reason=CATALOG_QUERY_FAILED count=1 error=0 sqlstate=none",
                "(c) UNDETERMINED reason=CATALOG_QUERY_FAILED count=1 error=0 sqlstate=none",
                "(d) UNDETERMINED reason=CATALOG_QUERY_FAILED count=3 error=0 sqlstate=none",
                "(e) UNDETERMINED reason=CATALOG_QUERY_FAILED count=1 error=0 sqlstate=none",
                "(f) UNDETERMINED reason=CATALOG_QUERY_FAILED count=2 error=0 sqlstate=none",
                "(g) UNDETERMINED reason=CATALOG_QUERY_FAILED count=1 error=0 sqlstate=none",
                "RESULT UNDETERMINED");
    }

    @Test
    @DisplayName("PAR-2 a password, secret or credential option in any case is wrong usage, named but its value never echoed")
    void secretsOnTheCommandLineAreRefused() {
        String value = "v" + UUID.randomUUID();
        for (String option : new String[] {"--Password", "--db.SECRET", "--a8-credential", "--spring.datasource.password"}) {
            out.reset();
            err.reset();

            assertThat(run("--connection=probe", option + "=" + value)).as(option).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
            assertThat(output()).as(option).doesNotContain(value).doesNotContain("RESULT");
        }
    }

    @Test
    @DisplayName("PAR-2 复审 M1 a connection variable or SPRING_APPLICATION_JSON on the command line is wrong usage, its value never echoed")
    void connectionSettingsOnTheCommandLineAreRefused() {
        String value = "v" + UUID.randomUUID();
        for (String option : new String[] {"--A8_DB_URL=jdbc:sqlserver://h;password=" + value, "--readonly.probe.db.username=" + value,
                "--spring.application.json={\"A8_DB_PASSWORD\":\"" + value + "\"}"}) {
            out.reset();
            err.reset();

            assertThat(run("--connection=integration-seeyon-background", option)).as(option).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
            assertThat(output()).as(option).doesNotContain(value).doesNotContain("RESULT");
        }
    }

    @Test
    @DisplayName("PAR-2 复审 M1 settings from -D, the command line or SPRING_APPLICATION_JSON are not read: without the process environment the connection is not configured")
    void onlyTheProcessEnvironmentIsRead() {
        environment.setProperty("READONLY_PROBE_DB_URL", "jdbc:sqlserver://127.0.0.1:1;databaseName=x");
        environment.setProperty("READONLY_PROBE_DB_USERNAME", "reader");
        environment.setProperty("READONLY_PROBE_DB_PASSWORD", "p" + UUID.randomUUID());

        assertThat(run("--connection=probe")).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(output()).contains("READONLY_PROBE_DB_URL").contains("READONLY_PROBE_DB_PASSWORD").doesNotContain("RESULT");
    }

    @Test
    @DisplayName("PAR-2 复审 M1 the process environment wins over any other property source of the same name")
    void processEnvironmentWinsOverOtherSources() throws IOException {
        shortBackgroundLimits();
        processEnvironment.put("A8_DB_URL", closedSqlServerUrl());
        processEnvironment.put("A8_DB_USERNAME", "reader");
        processEnvironment.put("A8_DB_PASSWORD", "p" + UUID.randomUUID());
        environment.setProperty("A8_DB_URL", "jdbc:postgresql://127.0.0.1:1/other");

        assertThat(run("--connection=integration-seeyon-background")).as("checked the process environment's SQL Server URL, could not connect\n%s", output())
                .isEqualTo(ReadOnlyVerificationCommand.EXIT_UNDETERMINED);
        assertThat(output()).contains("RESULT UNDETERMINED").doesNotContain("postgresql");
    }

    @Test
    @DisplayName("PAR-2 a missing, repeated or unknown connection name and an argument that is not an option are wrong usage")
    void connectionNameIsRequired() {
        assertThat(run()).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(run("--connection=no-such-connection")).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(run("--connection=probe", "--connection=legacy-approval")).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        String stray = "s" + UUID.randomUUID();
        assertThat(run("--connection=probe", stray)).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(output()).doesNotContain(stray).doesNotContain("RESULT");
    }

    @Test
    @DisplayName("IMP-1021-PAR-04 老待办连接已退役：--connection=legacy-approval 与 integration-legacy-inbox 同其他未知名一样是用法错误（退出码 2），不核实，提示里不再列出它")
    void theRetiredConnectionNamesAreUnknown() {
        for (String retired : new String[] {"legacy-approval", "integration-legacy-inbox"}) {
            out.reset();
            err.reset();

            assertThat(run("--connection=" + retired)).as(retired).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
            assertThat(err.toString(StandardCharsets.UTF_8)).as(retired).contains("must name one of").doesNotContain("integration-legacy-inbox");
            assertThat(output()).as(retired).doesNotContain("RESULT");
        }
    }

    @Test
    @DisplayName("PAR-2 复审 Low the command refuses to run together with the migrate or identity-operations profile")
    void runsAlone() {
        for (String other : new String[] {"migrate", "identity-operations"}) {
            environment.setActiveProfiles(ReadOnlyVerificationCommand.PROFILE, other);

            assertThat(run("--connection=probe")).as(other).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        }
        assertThat(output()).doesNotContain("RESULT");
    }

    @Test
    @DisplayName("PAR-2 rule 6: a connection whose environment variables are missing is wrong usage naming the variables, not checked")
    void missingVariablesAreNamed() {
        processEnvironment.put("READONLY_PROBE_DB_URL", "jdbc:sqlserver://127.0.0.1:1;databaseName=x");

        assertThat(run("--connection=probe")).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(output()).contains("READONLY_PROBE_DB_USERNAME").contains("READONLY_PROBE_DB_PASSWORD").doesNotContain("RESULT");
    }

    @Test
    @DisplayName("PAR-2 复审 M3 a URL other than SQL Server, PostgreSQL included, is wrong usage, and the URL is not printed")
    void onlySqlServerUrls() {
        String password = "p" + UUID.randomUUID();
        processEnvironment.put("A8_DB_USERNAME", "reader");
        processEnvironment.put("A8_DB_PASSWORD", password);
        for (String url : new String[] {"jdbc:mysql://host/db?password=" + password, "jdbc:postgresql://host/db?password=" + password}) {
            out.reset();
            err.reset();
            processEnvironment.put("A8_DB_URL", url);

            assertThat(run("--connection=integration-seeyon-background")).as(url).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
            assertThat(output()).contains("A8_DB_URL").doesNotContain(password).doesNotContain("host/db").doesNotContain("mysql").doesNotContain("postgresql").doesNotContain("RESULT");
        }
    }

    @Test
    @DisplayName("PAR-2 复审 M2 with a pool logger set to DEBUG (logging.level.com.zaxxer.hikari.HikariConfig) the silenced logs carry no URL and no login")
    void silencedLogsCarryNoConnectionSettings() throws IOException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Map<Logger, Level> levels = new HashMap<>();
        context.getLoggerList().forEach(logger -> levels.put(logger, logger.getLevel()));
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(captured);
        LoggingSystem.get(getClass().getClassLoader()).setLogLevel("com.zaxxer.hikari.HikariConfig", LogLevel.DEBUG);
        String login = "login" + UUID.randomUUID().toString().replace("-", "");
        String url = closedSqlServerUrl();
        Limits limits = new Limits(Duration.ofMillis(500), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2));
        try {
            ExternalConnectionFactory.onOneConnection(limits, "control", url, login, "p", source -> 0, failure -> 1);
            assertThat(captured.list).as("control: the DEBUG pool logger writes the login").anyMatch(event -> event.getFormattedMessage().contains(login));
            captured.list.clear();

            assertThat(ReadOnlyVerificationCommand.silenceLogs()).isEmpty();
            ExternalConnectionFactory.onOneConnection(limits, "silenced", url, login, "p", source -> 0, failure -> 1);

            assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(message -> message.contains(login) || message.contains(url));
            assertThat(captured.list).as("nothing is logged at all").isEmpty();
        } finally {
            root.detachAppender(captured);
            context.getLoggerList().forEach(logger -> logger.setLevel(levels.get(logger)));
        }
    }
}
