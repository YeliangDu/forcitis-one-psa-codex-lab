package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestMssql;
import com.forcitis.one.TestPostgres;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PAR-2, the read-only verification command (ADR-0037 item 4, one-coordinator's terms in HANDOFF_one-1a-next-2 section 6; used by the C-14
 * runbook): the packaged application, run as on ECS ({@code java -jar}, the connection settings and passwords only from the same environment
 * variables as the running service), checks the login of one named read-only connection with the start-up check's own judgement and prints
 * one line per item (a)–(g) and a result line, with an exit code per result. The contract this class assumes (profile, option, line format,
 * exit codes, environment) is in READONLY_VERIFY_ACCEPTANCE.md next to it; nothing here refers to a production class, so the test sources
 * compile before the command exists.
 *
 * <p>Black box: a separate JVM per case, against a real SQL Server whose logins come from {@link ReadOnlyBaselineFixture}. Each case also
 * proves the command stayed off the network (no server socket opened, {@link ListeningSocketProbe}) and off the application database (the
 * empty PostgreSQL given in SPRING_DATASOURCE_* has no table and no schema afterwards, so no Flyway history and no migration).
 */
class ReadOnlyVerificationCommandIT {

    static final String PROFILE = "read-only-verification";
    static final String CONNECTION_OPTION = "--connection=";

    static final int EXIT_PASSED = 0;
    static final int EXIT_USAGE = 2;
    static final int EXIT_VIOLATED = 3;
    static final int EXIT_UNDETERMINED = 4;

    static final List<Character> ITEMS = List.of('a', 'b', 'c', 'd', 'e', 'f', 'g');
    static final Pattern ITEM_LINE = Pattern.compile("^\\(([a-g])\\) (PASSED|VIOLATED|UNDETERMINED)((?: [a-z]+=[A-Za-z0-9_.-]+)*)$", Pattern.MULTILINE);
    static final Pattern RESULT_LINE = Pattern.compile("^RESULT (PASSED|VIOLATED|UNDETERMINED)$", Pattern.MULTILINE);

    /** READONLY_VERIFY_ACCEPTANCE.md section 3a: a quoted value is a catalog name with escapes, a bare one is a code or a placeholder. */
    static final String LOCATOR_VALUE = "(?:\"(?:[^\"\\\\\\p{Cntrl}\\u2028\\u2029]|\\\\[\"\\\\nrt]|\\\\u[0-9A-F]{4})*\"|[A-Z0-9_-]+)";
    static final Pattern LOCATOR_LINE = Pattern.compile("^  \\(([fg])\\) (violation|overridden)((?: [a-zA-Z]+=" + LOCATOR_VALUE + ")+)$");
    static final Pattern LOCATOR_FIELD = Pattern.compile(" ([a-zA-Z]+)=(" + LOCATOR_VALUE + ")");
    static final List<String> SYSTEM_DATABASE_FIELDS = List.of("reason", "database");
    static final List<String> VIOLATION_FIELDS = List.of("reason", "class", "schema", "object", "objectId", "permission", "state", "grantee", "granteeType");
    static final List<String> OVERRIDDEN_FIELDS = List.of("class", "schema", "object", "objectId", "permission", "state", "grantee", "granteeType", "by");
    static final AtomicInteger ISOLATED_DATABASES = new AtomicInteger();

    static final Duration COMMAND_TIME_LIMIT = Duration.ofSeconds(120);

    static final String SA_PASSWORD = TestMssql.SA_PASSWORD;
    static final AtomicInteger LOGINS = new AtomicInteger();

    static final PostgreSQLContainer APPLICATION_DATABASE = TestPostgres.container();

    static final GenericContainer<?> MSSQL = TestMssql.shared();

    static final String A8_WORLD = TestMssql.database(ReadOnlyVerificationCommandIT.class, "a8world").name();
    static final String LEGACY_WORLD = TestMssql.database(ReadOnlyVerificationCommandIT.class, "legacyworld").name();
    static final String OTHER_WORLD = TestMssql.database(ReadOnlyVerificationCommandIT.class, "otherworld").name();

    static final Path RELEASE_JAR;
    static final Path PROBE_CLASSES;
    static final Map<ReadOnlyConnection, Account> VIOLATING = new EnumMap<>(ReadOnlyConnection.class);

    static {
        APPLICATION_DATABASE.start();
        createExternalWorld();
        RELEASE_JAR = releaseJar();
        PROBE_CLASSES = probeClasses();
        for (ReadOnlyConnection connection : ReadOnlyConnection.values()) {
            Account account = baselineAccount(connection);
            try {
                run(connection.database, "ALTER ROLE db_datareader ADD MEMBER " + q(account.login()));
            } catch (SQLException e) {
                throw new IllegalStateException("cannot prepare the violating login of " + connection, e);
            }
            VIOLATING.put(connection, account);
        }
    }

    /** The connection names of the running service (the pool and metric tag names) and the environment variables of each, as in one.env. */
    enum ReadOnlyConnection {
        A8("integration-seeyon-background", A8_WORLD, "A8_DB_URL", "A8_DB_USERNAME", "A8_DB_PASSWORD"),
        LEGACY_NAMES("integration-legacy", LEGACY_WORLD, "LEGACY_DB_RO_URL", "LEGACY_DB_RO_USERNAME", "LEGACY_DB_RO_PASSWORD"),
        INTEGRATION_LEGACY_INBOX("integration-legacy-inbox", LEGACY_WORLD, "FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_URL", "FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_USERNAME",
                "FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD");

        final String connectionName;
        final String database;
        final String urlVariable;
        final String usernameVariable;
        final String passwordVariable;

        ReadOnlyConnection(String connectionName, String database, String urlVariable, String usernameVariable, String passwordVariable) {
            this.connectionName = connectionName;
            this.database = database;
            this.urlVariable = urlVariable;
            this.usernameVariable = usernameVariable;
            this.passwordVariable = passwordVariable;
        }
    }

    record Account(ReadOnlyConnection connection, String login, String password, String url) {
    }

    record Sql(String database, String statement) {
    }

    record Deviation(String name, char item, Function<Account, List<Sql>> apply) {

        @Override
        public String toString() {
            return name;
        }
    }

    record ItemLine(char item, String verdict, String tokens) {
    }

    /** fields: key to the value as printed, quotes and escapes included; equality ignores the order, which locatorFieldsInContractOrder checks. */
    record Locator(char item, String kind, Map<String, String> fields) {
    }

    record CommandRun(int exitCode, String output) {

        /**
         * Every locator line, each checked to follow its own item line directly (only other locator lines of that item in between), and no
         * line that looks like a locator line ("  (") but breaks the format.
         */
        List<Locator> locators() {
            List<Locator> found = new ArrayList<>();
            Character current = null;
            for (String line : output.split("\n", -1)) {
                Matcher item = ITEM_LINE.matcher(line);
                Matcher locator = LOCATOR_LINE.matcher(line);
                if (item.matches()) {
                    current = item.group(1).charAt(0);
                } else if (locator.matches()) {
                    char of = locator.group(1).charAt(0);
                    assertThat(current).as("the locator line \"%s\" follows the line of its item (%s) directly\n%s", line, of, output).isEqualTo(of);
                    Map<String, String> fields = new LinkedHashMap<>();
                    Matcher field = LOCATOR_FIELD.matcher(locator.group(3));
                    while (field.find()) {
                        assertThat(fields.put(field.group(1), field.group(2))).as("field %s twice in \"%s\"", field.group(1), line).isNull();
                    }
                    found.add(new Locator(of, locator.group(2), fields));
                } else {
                    assertThat(line).as("a line starting with \"  (\" is a locator line and must match the format of section 3a\n%s", output)
                            .doesNotStartWith("  (");
                    current = null;
                }
            }
            return found;
        }

        List<Locator> locators(char item, String kind) {
            return locators().stream().filter(l -> l.item() == item && l.kind().equals(kind)).toList();
        }

        List<ItemLine> items() {
            List<ItemLine> lines = new ArrayList<>();
            Matcher m = ITEM_LINE.matcher(output);
            while (m.find()) {
                lines.add(new ItemLine(m.group(1).charAt(0), m.group(2), m.group(3)));
            }
            return lines;
        }

        ItemLine item(char item) {
            List<ItemLine> found = items().stream().filter(line -> line.item() == item).toList();
            assertThat(found).as("exactly one line \"(%s) <PASSED|VIOLATED|UNDETERMINED>\" in the output:\n%s", item, output).hasSize(1);
            return found.getFirst();
        }

        List<String> results() {
            List<String> results = new ArrayList<>();
            Matcher m = RESULT_LINE.matcher(output);
            while (m.find()) {
                results.add(m.group(1));
            }
            return results;
        }
    }

    static final List<Deviation> DEVIATIONS = List.of(
            new Deviation("库级 DENY INSERT 被撤并 GRANT INSERT ON SCHEMA::dbo", 'b', a -> List.of(
                    new Sql(a.connection().database, "REVOKE INSERT FROM " + q(a.login())),
                    new Sql(a.connection().database, "GRANT INSERT ON SCHEMA::dbo TO " + q(a.login())))),
            new Deviation("加入 db_datareader", 'c', a -> List.of(
                    new Sql(a.connection().database, "ALTER ROLE db_datareader ADD MEMBER " + q(a.login())))),
            new Deviation("账号拥有一个 schema", 'e', a -> List.of(
                    new Sql(a.connection().database, "CREATE SCHEMA " + q(a.login() + "_owned") + " AUTHORIZATION " + q(a.login())))),
            new Deviation("能访问基线外的另一个用户库", 'f', a -> List.of(
                    new Sql(OTHER_WORLD, "CREATE USER " + q(a.login()) + " FOR LOGIN " + q(a.login())))));

    static Stream<Arguments> deviations() {
        return DEVIATIONS.stream().map(Arguments::of);
    }

    @Test
    @DisplayName("PAR-2 对照：探针能发现打开监听套接字的进程（否则「只读核实命令不监听任何端口」的断言没有意义）")
    void probeSeesAProcessThatListens() throws Exception {
        List<String> command = List.of(java(), "-Xbootclasspath/a:" + PROBE_CLASSES, "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED",
                "-Djava.nio.channels.spi.SelectorProvider=" + ListeningSocketProbe.class.getName(), ListeningSocketProbe.class.getName());

        CommandRun run = launch(command, Map.of());

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains(ListeningSocketProbe.MARKER);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ReadOnlyConnection.class)
    @DisplayName("ADR-0037 第 4 项 (a)–(g)、C-14：只读核实命令对符合 RDS 基线的账号七项都标通过、结果通过、退出 0；env 文件中另两条连接配的违规账号不影响所核实的连接")
    void baselineAccountPassesEveryItem(ReadOnlyConnection connection) throws Exception {
        Account account = baselineAccount(connection);
        assertMatchesTheRdsBaseline(account);

        CommandRun run = verify(connection, account);

        assertThat(run.exitCode()).as("exit code of a pass\n%s", run.output()).isEqualTo(EXIT_PASSED);
        for (char item : ITEMS) {
            assertThat(run.item(item).verdict()).as("(%s)\n%s", item, run.output()).isEqualTo("PASSED");
        }
        assertThat(run.results()).as(run.output()).containsExactly("PASSED");
        assertTellsNothingSecret(run, account);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deviations")
    @DisplayName("ADR-0037 第 4 项第 7 条、C-14：账号偏离基线时只读核实命令把对应项标违规、结果违规、退出违规码")
    void deviationIsMarkedAsViolated(Deviation deviation) throws Exception {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        assertMatchesTheRdsBaseline(account);
        for (Sql sql : deviation.apply().apply(account)) {
            run(sql.database(), sql.statement());
        }

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as("exit code of a violation\n%s", run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item(deviation.item()).verdict()).as("(%s) for %s\n%s", deviation.item(), deviation, run.output()).isEqualTo("VIOLATED");
        assertThat(run.items()).as("one line per item (a)–(g)\n%s", run.output()).extracting(ItemLine::item).containsExactlyElementsOf(ITEMS);
        assertThat(run.results()).as(run.output()).containsExactly("VIOLATED");
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("ADR-0037 第 3 项第 5 条、第 4 项 (c)、C-14：违规只给项目标记，不输出判定原因文本（其中有角色名）：以标记值命名的自定义角色不出现在输出中")
    void violationDoesNotTellTheNamesBehindIt() throws Exception {
        Account account = baselineAccount(ReadOnlyConnection.LEGACY_NAMES);
        String marker = marker();
        String role = "role_" + marker;
        run(account.connection().database, "CREATE ROLE " + q(role), "ALTER ROLE " + q(role) + " ADD MEMBER " + q(account.login()));

        CommandRun run = verify(ReadOnlyConnection.LEGACY_NAMES, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('c').verdict()).as(run.output()).isEqualTo("VIOLATED");
        assertThat(run.output()).as("the role's name is in the check's reason text; the command prints the item only").doesNotContain(marker);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位（one-coordinator 2026-10-15）、ADR-0037 第 4 项 (f)、复审 H-2：账号在系统库 master、msdb 有自己的用户时 (f) 违规、计数 2，项行之后每库一条定位行带库名，退出违规码；其他项不出定位行")
    void systemDatabaseWithAnOwnUserIsLocatedByName() throws Exception {
        Account account = baselineAccount(ReadOnlyConnection.LEGACY_NAMES);
        run("master", "CREATE USER " + q(account.login()) + " FOR LOGIN " + q(account.login()));
        run("msdb", "CREATE USER " + q(account.login()) + " FOR LOGIN " + q(account.login()));

        CommandRun run = verify(ReadOnlyConnection.LEGACY_NAMES, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('f').verdict()).as(run.output()).isEqualTo("VIOLATED");
        assertThat(run.item('f').tokens()).as(run.output()).contains(" reason=SYSTEM_DATABASE_OWN_USER count=2");
        assertThat(run.locators()).as("one locator line per system database with a user of the login's own, nothing else\n%s", run.output())
                .containsExactlyInAnyOrder(
                        new Locator('f', "violation", fields("reason", "SYSTEM_DATABASE_OWN_USER", "database", quoted("master"))),
                        new Locator('f', "violation", fields("reason", "SYSTEM_DATABASE_OWN_USER", "database", quoted("msdb"))));
        assertLocatorFieldsInContractOrder(run);
        for (char item : "abcdeg".toCharArray()) {
            assertThat(run.item(item).verdict()).as("(%s)\n%s", item, run.output()).isEqualTo("PASSED");
        }
        assertThat(run.results()).as(run.output()).containsExactly("VIOLATED");
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位、ADR-0037 第 4 项 (b) 收窄 RULING-ADR37-10（one-coordinator 2026-10-15，安全判定放宽）：public 对象级 GRANT EXECUTE 被本账号库级 DENY EXECUTE 覆盖时 (g) 通过、输出一条 overridden 信息行（被拒绝覆盖的授权）、结果通过、退出 0（rc.2 A8 账号的情形）；账号看不见该过程，schema、object 为 -，objectId 为其编号（one-1a-par 裁定：元数据可见性，方向 A）")
    void publicExecuteOverriddenByTheDatabaseDenyPasses() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String procedure = "p_" + marker();
        runIn(account, "CREATE PROCEDURE dbo." + q(procedure) + " AS SELECT 1", "GRANT EXECUTE ON OBJECT::dbo." + q(procedure) + " TO public");
        long procedureId = objectId(account, "dbo." + q(procedure));
        assertThat(loginSeesObject(account, procedureId)).as("the fixture: the login's DENY EXECUTE hides the procedure from it").isFalse();

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as("a grant the login's own database-level DENY of the same permission overrides is no violation\n%s", run.output())
                .isEqualTo(EXIT_PASSED);
        for (char item : ITEMS) {
            assertThat(run.item(item).verdict()).as("(%s)\n%s", item, run.output()).isEqualTo("PASSED");
        }
        assertThat(run.locators()).as("one information line under (g), nothing counted as a violation\n%s", run.output()).containsExactly(
                overridden("OBJECT_OR_COLUMN", ABSENT, ABSENT, procedureId, "EXECUTE", PUBLIC, "DATABASE_DENY"));
        assertLocatorFieldsInContractOrder(run);
        assertThat(run.results()).as(run.output()).containsExactly("PASSED");
        assertThat(run.output()).as("a name the login cannot see is not printed").doesNotContain(procedure);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位、RULING-ADR37-10 收窄的安全条件 1、2、5、复审 H-1：public 对象级 GRANT CONTROL 与 GRANT TAKE OWNERSHIP 不在库级 DENY 清单内，即使本账号另有同名 schema 级 DENY 仍各算一条违规并带定位（账号看得见该表，带名称）；同时被 DENY 覆盖的 GRANT EXECUTE 只出 overridden 信息行、不计数（看不见，名称为 -、带 objectId）")
    void controlAndTakeOwnershipStayViolationsBesideAnOverriddenGrant() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String table = "t_" + marker();
        String procedure = "p_" + marker();
        runIn(account, "CREATE TABLE dbo." + q(table) + " (id bigint)", "CREATE PROCEDURE dbo." + q(procedure) + " AS SELECT 1",
                "GRANT CONTROL ON OBJECT::dbo." + q(table) + " TO public", "GRANT TAKE OWNERSHIP ON OBJECT::dbo." + q(table) + " TO public",
                "GRANT EXECUTE ON OBJECT::dbo." + q(procedure) + " TO public",
                "DENY TAKE OWNERSHIP ON SCHEMA::dbo TO " + q(account.login()));
        long tableId = objectId(account, "dbo." + q(table));
        long procedureId = objectId(account, "dbo." + q(procedure));
        assertThat(loginSeesObject(account, tableId)).as("the fixture: the login sees the table").isTrue();
        assertThat(loginSeesObject(account, procedureId)).as("the fixture: the login does not see the procedure").isFalse();

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('g').verdict()).as(run.output()).isEqualTo("VIOLATED");
        assertThat(run.item('g').tokens()).as("count is the number of violation lines\n%s", run.output())
                .contains(" reason=SECURABLE_GRANT_BEYOND_SELECT count=2");
        assertThat(run.locators('g', "violation")).as(run.output()).containsExactlyInAnyOrder(
                violation("OBJECT_OR_COLUMN", quoted("dbo"), quoted(table), tableId, "CONTROL", PUBLIC),
                violation("OBJECT_OR_COLUMN", quoted("dbo"), quoted(table), tableId, "TAKE OWNERSHIP", PUBLIC));
        assertThat(run.locators('g', "overridden")).as(run.output()).containsExactly(
                overridden("OBJECT_OR_COLUMN", ABSENT, ABSENT, procedureId, "EXECUTE", PUBLIC, "DATABASE_DENY"));
        assertThat(run.locators()).as("no locator line for another item\n%s", run.output()).allSatisfy(l -> assertThat(l.item()).isEqualTo('g'));
        assertLocatorFieldsInContractOrder(run);
        assertThat(run.results()).as(run.output()).containsExactly("VIOLATED");
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位（字段缺失出占位不省略）：schema 级 GRANT 的 object 为占位 -、objectId 为 schema 编号；public 的 schema 级 GRANT CONTROL 违规、schema 级 GRANT INSERT 被库级 DENY INSERT 覆盖")
    void schemaLevelGrantsCarryTheObjectPlaceholder() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String schema = "s_" + marker();
        runIn(account, "CREATE SCHEMA " + q(schema) + " AUTHORIZATION dbo", "GRANT CONTROL ON SCHEMA::" + q(schema) + " TO public",
                "GRANT INSERT ON SCHEMA::" + q(schema) + " TO public");
        long schemaId = schemaId(account, schema);
        assertThat(loginSeesSchema(account, schemaId)).as("the fixture: the login sees the schema").isTrue();

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('g').tokens()).as(run.output()).contains(" reason=SECURABLE_GRANT_BEYOND_SELECT count=1");
        assertThat(run.locators()).as(run.output()).containsExactlyInAnyOrder(
                violation("SCHEMA", quoted(schema), ABSENT, schemaId, "CONTROL", PUBLIC),
                overridden("SCHEMA", quoted(schema), ABSENT, schemaId, "INSERT", PUBLIC, "DATABASE_DENY"));
        assertLocatorFieldsInContractOrder(run);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位、安全条件 1（按权限名与 schema 精确匹配）与元数据可见性（one-1a-par 裁定方向 A）：库级 DENY INSERT、DENY EXECUTE 被撤，只剩 schema::dbo 上的同名 DENY 时——dbo 下账号看得见的表的 public GRANT INSERT 为 overridden by=SCHEMA_DENY；另一 schema 下的同类授权违规；dbo 下账号看不见的过程的 public GRANT EXECUTE 无法确认所属 schema，按保守口径违规（不按看不见即被覆盖）；(b) 违规、退出违规码")
    void aSchemaDenyOverridesOnlyInItsOwnSchema() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String schema = "s_" + marker();
        String inDbo = "t_" + marker();
        String elsewhere = "t_" + marker();
        String hidden = "p_" + marker();
        runIn(account, "REVOKE INSERT, EXECUTE FROM " + q(account.login()), "DENY INSERT, EXECUTE ON SCHEMA::dbo TO " + q(account.login()),
                "CREATE SCHEMA " + q(schema) + " AUTHORIZATION dbo",
                "CREATE TABLE dbo." + q(inDbo) + " (id bigint)", "GRANT INSERT ON OBJECT::dbo." + q(inDbo) + " TO public",
                "CREATE TABLE " + q(schema) + "." + q(elsewhere) + " (id bigint)", "GRANT INSERT ON OBJECT::" + q(schema) + "." + q(elsewhere) + " TO public",
                "CREATE PROCEDURE dbo." + q(hidden) + " AS SELECT 1", "GRANT EXECUTE ON OBJECT::dbo." + q(hidden) + " TO public");
        long inDboId = objectId(account, "dbo." + q(inDbo));
        long elsewhereId = objectId(account, q(schema) + "." + q(elsewhere));
        long hiddenId = objectId(account, "dbo." + q(hidden));
        assertThat(loginSeesObject(account, inDboId)).as("the fixture: the database-level GRANT SELECT shows the login the table in dbo").isTrue();
        assertThat(loginSeesObject(account, elsewhereId)).as("the fixture: the login sees the table in the other schema").isTrue();
        assertThat(loginSeesObject(account, hiddenId)).as("the fixture: the schema DENY EXECUTE hides the procedure from the login").isFalse();

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('b').verdict()).as("the database-level DENY INSERT and EXECUTE are missing\n%s", run.output()).isEqualTo("VIOLATED");
        assertThat(run.item('g').verdict()).as(run.output()).isEqualTo("VIOLATED");
        assertThat(run.item('g').tokens()).as(run.output()).contains(" reason=SECURABLE_GRANT_BEYOND_SELECT count=2");
        assertThat(run.locators()).as(run.output()).containsExactlyInAnyOrder(
                violation("OBJECT_OR_COLUMN", quoted(schema), quoted(elsewhere), elsewhereId, "INSERT", PUBLIC),
                violation("OBJECT_OR_COLUMN", ABSENT, ABSENT, hiddenId, "EXECUTE", PUBLIC),
                overridden("OBJECT_OR_COLUMN", quoted("dbo"), quoted(inDbo), inDboId, "INSERT", PUBLIC, "SCHEMA_DENY"));
        assertLocatorFieldsInContractOrder(run);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位、opus 安全复审 Low-2：列级 GRANT UPDATE（两列）遇本账号同名 schema 级 DENY UPDATE（库级 DENY UPDATE 被撤，账号看得见所属 schema 的表）时折叠为表上一条 overridden 行、by=SCHEMA_DENY；(g) 通过，(b) 违规、退出违规码")
    void columnGrantsFoldIntoTheTableAndAreOverriddenByTheSchemaDeny() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String table = "t_" + marker();
        runIn(account, "REVOKE UPDATE FROM " + q(account.login()), "DENY UPDATE ON SCHEMA::dbo TO " + q(account.login()),
                "CREATE TABLE dbo." + q(table) + " (a bigint, b bigint)", "GRANT UPDATE (a) ON OBJECT::dbo." + q(table) + " TO public",
                "GRANT UPDATE (b) ON OBJECT::dbo." + q(table) + " TO public");
        long tableId = objectId(account, "dbo." + q(table));
        assertThat(loginSeesObject(account, tableId)).as("the fixture: the database-level GRANT SELECT shows the login the table").isTrue();

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('b').verdict()).as("the database-level DENY UPDATE is missing\n%s", run.output()).isEqualTo("VIOLATED");
        assertThat(run.item('g').verdict()).as("both column grants are overridden by the schema DENY\n%s", run.output()).isEqualTo("PASSED");
        assertThat(run.locators()).as("the two column grants fold into one line on the table\n%s", run.output()).containsExactly(
                overridden("OBJECT_OR_COLUMN", quoted("dbo"), quoted(table), tableId, "UPDATE", PUBLIC, "SCHEMA_DENY"));
        assertLocatorFieldsInContractOrder(run);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位、opus 安全复审 Medium-1（one-1a-par 收窄）：只有对象级、列级与 schema 级 GRANT 可被覆盖——TYPE 上的 public GRANT REFERENCES 即使本账号有库级 DENY REFERENCES 仍违规并带定位（class=TYPE，objectId 为 user_type_id）")
    void grantOnATypeStaysAViolation() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String type = "ty_" + marker();
        runIn(account, "CREATE TYPE dbo." + q(type) + " FROM int", "GRANT REFERENCES ON TYPE::dbo." + q(type) + " TO public");
        long typeId = adminLong(account, "SELECT TYPE_ID(N'dbo." + q(type) + "')");
        boolean seen = loginCounts(account, "SELECT COUNT(*) FROM sys.types WHERE user_type_id = " + typeId) == 1;

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('g').verdict()).as("a TYPE grant is never overridden\n%s", run.output()).isEqualTo("VIOLATED");
        assertThat(run.item('g').tokens()).as(run.output()).contains(" reason=SECURABLE_GRANT_BEYOND_SELECT count=1");
        assertThat(run.locators()).as("names as far as the login can see the type (section 3a)\n%s", run.output()).containsExactly(
                violation("TYPE", seen ? quoted("dbo") : ABSENT, seen ? quoted(type) : ABSENT, typeId, "REFERENCES", PUBLIC));
        assertLocatorFieldsInContractOrder(run);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位、安全条件 3：本账号一条 DENY 也查不到（库级 11 项全被撤）时，可被覆盖的 public GRANT EXECUTE 判 (g) 判定不了而非被拒绝覆盖，不出 overridden 行；(b) 违规、退出违规码")
    void noDenyAnswerLeavesTheGrantUndetermined() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String procedure = "p_" + marker();
        runIn(account, "REVOKE " + ReadOnlyBaselineFixture.DATABASE_DENIES + " FROM " + q(account.login()),
                "CREATE PROCEDURE dbo." + q(procedure) + " AS SELECT 1", "GRANT EXECUTE ON OBJECT::dbo." + q(procedure) + " TO public");

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('b').verdict()).as(run.output()).isEqualTo("VIOLATED");
        assertThat(run.item('g').verdict()).as("whether a DENY overrides the grant cannot be told: undetermined, not overridden\n%s", run.output())
                .isEqualTo("UNDETERMINED");
        assertThat(run.locators('g', "overridden")).as(run.output()).isEmpty();
        assertThat(run.results()).as(run.output()).containsExactly("VIOLATED");
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位：授给账号本人的对象级授权，grantee 为占位 SELF 而非数据库用户名（夹具与 RDS 上用户名即登录名，不得输出），granteeType 为 SQL_USER；被 DENY 覆盖的出 overridden 信息行（账号看不见该过程，名称为 -）、未覆盖的违规（看得见，带名称）")
    void grantsToTheLoginItselfShowTheSelfPlaceholder() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String table = "t_" + marker();
        String procedure = "p_" + marker();
        runIn(account, "CREATE TABLE dbo." + q(table) + " (id bigint)", "CREATE PROCEDURE dbo." + q(procedure) + " AS SELECT 1",
                "GRANT CONTROL ON OBJECT::dbo." + q(table) + " TO " + q(account.login()),
                "GRANT EXECUTE ON OBJECT::dbo." + q(procedure) + " TO " + q(account.login()));
        long tableId = objectId(account, "dbo." + q(table));
        long procedureId = objectId(account, "dbo." + q(procedure));
        assertThat(loginSeesObject(account, tableId)).as("the fixture: the login sees the table").isTrue();
        assertThat(loginSeesObject(account, procedureId)).as("the fixture: the login's DENY EXECUTE hides the procedure").isFalse();

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('g').tokens()).as(run.output()).contains(" reason=SECURABLE_GRANT_BEYOND_SELECT count=1");
        assertThat(run.locators()).as(run.output()).containsExactlyInAnyOrder(
                violation("OBJECT_OR_COLUMN", quoted("dbo"), quoted(table), tableId, "CONTROL", SELF),
                overridden("OBJECT_OR_COLUMN", ABSENT, ABSENT, procedureId, "EXECUTE", SELF, "DATABASE_DENY"));
        assertLocatorFieldsInContractOrder(run);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("C-14 定位（值的转义）：对象名含换行、回车、制表、控制字符、U+2028、双引号与反斜杠时定位值被转义、不打断行结构，伪造的 RESULT 行不出现")
    void locatorValuesAreEscaped() throws Exception {
        Account account = isolatedAccount(ReadOnlyConnection.A8);
        String mark = marker();
        String table = "t_" + mark + "\nRESULT PASSED\r\t\u0007\u2028\"\\x";
        runIn(account, "CREATE TABLE dbo." + q(table) + " (id bigint)", "GRANT CONTROL ON OBJECT::dbo." + q(table) + " TO public");
        long tableId = objectId(account, "dbo." + q(table));
        assertThat(loginSeesObject(account, tableId)).as("the fixture: the login sees the table").isTrue();

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.locators()).as(run.output()).containsExactly(
                violation("OBJECT_OR_COLUMN", quoted("dbo"), "\"t_" + mark + "\\nRESULT PASSED\\r\\t\\u0007\\u2028\\\"\\\\x\"", tableId, "CONTROL", PUBLIC));
        assertThat(run.results()).as("the name cannot add a result line\n%s", run.output()).containsExactly("VIOLATED");
        assertThat(run.output()).as("no raw line break, control character or line separator of the name reaches the output")
                .doesNotContain(mark + "\n").doesNotContain("\u0007").doesNotContain("\u2028");
        assertLocatorFieldsInContractOrder(run);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("ADR-0037 第 4 项第 2 条、C-14：确认违规与判定不了同在时结果为违规、退出违规码；判定不了的项带 SQL 错误号与 SQLState")
    void violationTakesPrecedenceOverUndetermined() throws Exception {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        run(account.connection().database, "DENY SELECT ON sys.database_permissions TO " + q(account.login()),
                "ALTER ROLE db_datareader ADD MEMBER " + q(account.login()));
        SQLException refused = catalogRefusal(account);

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_VIOLATED);
        assertThat(run.item('c').verdict()).as(run.output()).isEqualTo("VIOLATED");
        assertThat(run.items()).as("(b), (e) or (g) read sys.database_permissions and cannot be determined, with the SQL error\n%s", run.output())
                .filteredOn(line -> "beg".indexOf(line.item()) >= 0 && line.verdict().equals("UNDETERMINED"))
                .isNotEmpty().allSatisfy(line -> assertThat(line.tokens()).contains(sqlError(refused)));
        assertThat(run.results()).as(run.output()).containsExactly("VIOLATED");
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("ADR-0037 第 4 项第 2 条、C-14：校验查询被拒且无确认违规时结果判定不了、退出判定不了码，判定不了的项只带 SQL 错误号与 SQLState")
    void refusedCatalogQueryIsUndetermined() throws Exception {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        run(account.connection().database, "DENY SELECT ON sys.database_permissions TO " + q(account.login()));
        SQLException refused = catalogRefusal(account);

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_UNDETERMINED);
        assertThat(run.items()).as("one line per item (a)–(g)\n%s", run.output()).extracting(ItemLine::item).containsExactlyElementsOf(ITEMS);
        assertThat(run.items()).as("nothing confirmed\n%s", run.output()).extracting(ItemLine::verdict).doesNotContain("VIOLATED");
        assertThat(run.items()).filteredOn(line -> line.verdict().equals("UNDETERMINED")).as(run.output())
                .isNotEmpty().allSatisfy(line -> assertThat(line.tokens()).contains(sqlError(refused)));
        assertThat(run.results()).as(run.output()).containsExactly("UNDETERMINED");
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("ADR-0037 第 4 项第 2 条、C-14：目标库不存在（不可达）时七项都判定不了，只输出驱动报的错误号与 SQLState，不输出含库名的消息原文")
    void missingDatabaseIsUndeterminedWithoutTheMessage() throws Exception {
        Account baseline = baselineAccount(ReadOnlyConnection.INTEGRATION_LEGACY_INBOX);
        String marker = marker();
        Account account = new Account(baseline.connection(), baseline.login(), baseline.password(), url("nodb_" + marker));
        SQLException refused = connectionFailure(account);
        assertThat(refused.getErrorCode()).as("SQL Server: cannot open the database requested by the login").isEqualTo(4060);
        assertThat(refused.getMessage()).as("the driver's message names the database, so printing it would leak the marker").contains(marker);

        CommandRun run = verify(ReadOnlyConnection.INTEGRATION_LEGACY_INBOX, account);

        assertUndeterminedOnEveryItem(run, refused);
        assertThat(run.output()).as("no database message text").doesNotContain(marker);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("ADR-0037 第 4 项第 2 条、C-14：连不上（端口无人监听）时七项都判定不了、退出判定不了码，输出驱动报的错误号与 SQLState")
    void unreachableServerIsUndetermined() throws Exception {
        Account baseline = baselineAccount(ReadOnlyConnection.A8);
        int closed = freePort();
        Account account = new Account(baseline.connection(), baseline.login(), baseline.password(),
                "jdbc:sqlserver://127.0.0.1:" + closed + ";databaseName=" + baseline.connection().database + ";encrypt=true;trustServerCertificate=true");
        SQLException refused = connectionFailure(account);

        CommandRun run = verify(ReadOnlyConnection.A8, account);

        assertUndeterminedOnEveryItem(run, refused);
        assertTellsNothingSecret(run, account);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"--password", "--forcitis.platform.integration.seeyon.views.password", "--A8_DB_PASSWORD", "--spring.datasource.password"})
    @DisplayName("PAR-2 口令只从 env 文件读（ADR-0037 第 5 项、C-14 剧本「口令与记录」）：命令行出现口令类参数即拒绝，退出用法错误码，不做核实、不回显该值")
    void passwordOnTheCommandLineIsRefused(String option) throws Exception {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        String offered = "Cl1!" + UUID.randomUUID();

        CommandRun run = verify(ReadOnlyConnection.A8, account, option + "=" + offered);

        assertThat(run.exitCode()).as("a password on the command line is wrong usage; with the same environment and no such option it passes\n%s",
                run.output()).isEqualTo(EXIT_USAGE);
        assertThat(run.items()).as("refused before any check\n%s", run.output()).isEmpty();
        assertThat(run.results()).as(run.output()).isEmpty();
        assertThat(run.output()).doesNotContain(offered);
        assertTellsNothingSecret(run, account);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "no-such-connection"})
    @DisplayName("PAR-2：未指定连接名或连接名不是现有只读连接时退出用法错误码，不做核实")
    void missingOrUnknownConnectionIsWrongUsage(String connectionName) throws Exception {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        Map<String, String> environment = environment(Map.of(ReadOnlyConnection.A8, account));
        List<String> arguments = new ArrayList<>(List.of("--spring.profiles.active=" + PROFILE));
        if (!connectionName.isEmpty()) {
            arguments.add(CONNECTION_OPTION + connectionName);
        }

        CommandRun run = launch(command(arguments), environment);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_USAGE);
        assertThat(run.items()).as(run.output()).isEmpty();
        assertStayedOffTheNetworkAndTheApplicationDatabase(run);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("RULING-P3B-17：老待办连接已改名，--connection=legacy-approval 退出用法错误码、不做核实（无项行、无 RESULT 行），提示新名 integration-legacy-inbox")
    void retiredConnectionNameIsWrongUsageAndNamesTheNewOne() throws Exception {
        Account account = baselineAccount(ReadOnlyConnection.A8);
        Map<String, String> environment = environment(Map.of(ReadOnlyConnection.A8, account));
        List<String> arguments = List.of("--spring.profiles.active=" + PROFILE, CONNECTION_OPTION + "legacy-approval");

        CommandRun run = launch(command(arguments), environment);

        assertThat(run.exitCode()).as(run.output()).isEqualTo(EXIT_USAGE);
        assertThat(run.items()).as(run.output()).isEmpty();
        assertThat(run.results()).as(run.output()).isEmpty();
        assertThat(run.output()).as("the message names the new connection").contains(ReadOnlyConnection.INTEGRATION_LEGACY_INBOX.connectionName);
        assertStayedOffTheNetworkAndTheApplicationDatabase(run);
        assertTellsNothingSecret(run, account);
    }

    @Test
    @DisplayName("PAR-2 契约自检：退出码通过为 0，通过、违规、判定不了、用法错误两两不同，且都不是 Spring Boot 启动失败的 1")
    void exitCodesAreDistinct() {
        assertThat(List.of(EXIT_PASSED, EXIT_VIOLATED, EXIT_UNDETERMINED, EXIT_USAGE)).doesNotHaveDuplicates().doesNotContain(1);
        assertThat(EXIT_PASSED).isZero();
    }

    static void assertUndeterminedOnEveryItem(CommandRun run, SQLException cause) {
        assertThat(run.exitCode()).as("exit code of a check that cannot be determined\n%s", run.output()).isEqualTo(EXIT_UNDETERMINED);
        for (char item : ITEMS) {
            ItemLine line = run.item(item);
            assertThat(line.verdict()).as("(%s)\n%s", item, run.output()).isEqualTo("UNDETERMINED");
            assertThat(line.tokens()).as("(%s) carries the SQL error number and SQLState\n%s", item, run.output()).contains(sqlError(cause));
        }
        assertThat(run.results()).as(run.output()).containsExactly("UNDETERMINED");
    }

    /** Never the password, the login, the connection URL or a password of any other connection of the environment file. */
    static void assertTellsNothingSecret(CommandRun run, Account account) {
        assertThat(run.output()).as("password").doesNotContain(account.password());
        assertThat(run.output()).as("login of the connection string").doesNotContain(account.login());
        assertThat(run.output()).as("connection string").doesNotContain(account.url());
        assertThat(run.output()).as("application database password").doesNotContain(APPLICATION_DATABASE.getPassword() + "@")
                .doesNotContain("password=" + APPLICATION_DATABASE.getPassword());
        for (Account other : VIOLATING.values()) {
            assertThat(run.output()).as("password of another connection").doesNotContain(other.password());
        }
    }

    static void assertStayedOffTheNetworkAndTheApplicationDatabase(CommandRun run) throws SQLException {
        assertThat(run.output()).as("the command listens on no TCP port (no web server, no management port)").doesNotContain(ListeningSocketProbe.MARKER);
        try (Connection c = DriverManager.getConnection(APPLICATION_DATABASE.getJdbcUrl(), APPLICATION_DATABASE.getUsername(),
                APPLICATION_DATABASE.getPassword()); Statement s = c.createStatement()) {
            assertThat(strings(s, "SELECT table_schema || '.' || table_name FROM information_schema.tables "
                    + "WHERE table_schema NOT IN ('pg_catalog', 'information_schema')"))
                    .as("no Flyway history table and no migrated table in the application database").isEmpty();
            assertThat(strings(s, "SELECT schema_name FROM information_schema.schemata "
                    + "WHERE schema_name NOT IN ('public', 'information_schema') AND schema_name NOT LIKE 'pg\\_%'"))
                    .as("no schema created in the application database").isEmpty();
        }
    }

    /** The grantee and granteeType fields as printed. */
    record Grantee(String name, String type) {
    }

    static final Grantee PUBLIC = new Grantee(quoted("public"), "DATABASE_ROLE");
    static final Grantee SELF = new Grantee("SELF", "SQL_USER");
    static final String ABSENT = "-";

    /** A name as printed: in double quotes; only names without a character to escape are passed here. */
    static String quoted(String name) {
        return "\"" + name + "\"";
    }

    static Map<String, String> fields(String... keysAndValues) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            fields.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return fields;
    }

    /** schema and object as printed (quoted name, or ABSENT where the login cannot see the securable); objectId is the major_id. */
    static Locator violation(String securableClass, String schema, String object, long objectId, String permission, Grantee grantee) {
        return new Locator('g', "violation", fields("reason", "SECURABLE_GRANT_BEYOND_SELECT", "class", securableClass, "schema", schema, "object", object,
                "objectId", String.valueOf(objectId), "permission", quoted(permission), "state", "GRANT", "grantee", grantee.name(), "granteeType", grantee.type()));
    }

    static Locator overridden(String securableClass, String schema, String object, long objectId, String permission, Grantee grantee, String by) {
        return new Locator('g', "overridden", fields("class", securableClass, "schema", schema, "object", object, "objectId", String.valueOf(objectId),
                "permission", quoted(permission), "state", "GRANT", "grantee", grantee.name(), "granteeType", grantee.type(), "by", by));
    }

    /** The major_id of an object as sa reads it: the oracle for objectId. */
    static long objectId(Account account, String qualifiedName) throws SQLException {
        return adminLong(account, "SELECT OBJECT_ID(N'" + qualifiedName.replace("'", "''") + "')");
    }

    static long schemaId(Account account, String schema) throws SQLException {
        return adminLong(account, "SELECT SCHEMA_ID(N'" + schema.replace("'", "''") + "')");
    }

    private static long adminLong(Account account, String query) throws SQLException {
        try (Connection c = DriverManager.getConnection(account.url() + ";loginTimeout=30", "sa", SA_PASSWORD); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(query)) {
            assertThat(rs.next() && rs.getObject(1) != null).as("the fixture: %s", query).isTrue();
            return rs.getLong(1);
        }
    }

    /**
     * Metadata visibility, asked as the login itself: the premise of each expected name or placeholder. SQL Server shows the login a securable
     * only where it holds some permission on it that is not denied.
     */
    static boolean loginSeesObject(Account account, long objectId) throws SQLException {
        return loginCounts(account, "SELECT COUNT(*) FROM sys.objects WHERE object_id = " + objectId) == 1;
    }

    static boolean loginSeesSchema(Account account, long schemaId) throws SQLException {
        return loginCounts(account, "SELECT COUNT(*) FROM sys.schemas WHERE schema_id = " + schemaId) == 1;
    }

    private static long loginCounts(Account account, String query) throws SQLException {
        try (Connection c = DriverManager.getConnection(account.url() + ";loginTimeout=30", account.login(), account.password());
                Statement s = c.createStatement(); ResultSet rs = s.executeQuery(query)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Fixed field order (section 3a), so that a grep for "permission=... state=..." finds the line. */
    static void assertLocatorFieldsInContractOrder(CommandRun run) {
        for (Locator locator : run.locators()) {
            List<String> order = locator.item() == 'f' ? SYSTEM_DATABASE_FIELDS : locator.kind().equals("overridden") ? OVERRIDDEN_FIELDS : VIOLATION_FIELDS;
            assertThat(List.copyOf(locator.fields().keySet())).as("fields of %s\n%s", locator, run.output()).containsExactlyElementsOf(order);
        }
    }

    /**
     * A baseline login in a database of its own (one view, as the old-system accounts): grants to public there reach no other case's login.
     * The database name is not part of any assertion on names.
     */
    static Account isolatedAccount(ReadOnlyConnection connection) throws SQLException {
        String database = TestMssql.database(ReadOnlyVerificationCommandIT.class, "isolated_" + ISOLATED_DATABASES.incrementAndGet()).name();
        run(database, "CREATE TABLE dbo.base_rows (id bigint)", "CREATE VIEW dbo.v_rows AS SELECT id FROM dbo.base_rows");
        String login = TestMssql.login(ReadOnlyVerificationCommandIT.class, connection.name().toLowerCase(Locale.ROOT) + "_ro_" + LOGINS.incrementAndGet());
        String password = "Ro1!" + UUID.randomUUID();
        ReadOnlyBaselineFixture.createSingleViewReader(ReadOnlyVerificationCommandIT::run, database, "dbo.v_rows", login, password);
        Account account = new Account(connection, login, password, url(database));
        ReadOnlyBaselineFixture.assertMatchesTheRdsBaseline(account.url(), database, login, password);
        return account;
    }

    /** As sa, in the account's own database (the one of its URL). */
    static void runIn(Account account, String... statements) throws SQLException {
        try (Connection c = DriverManager.getConnection(account.url() + ";loginTimeout=30", "sa", SA_PASSWORD); Statement s = c.createStatement()) {
            for (String sql : statements) {
                s.execute(sql);
            }
        }
    }

    static String sqlError(SQLException cause) {
        return " error=" + cause.getErrorCode() + " sqlstate=" + cause.getSQLState();
    }

    static CommandRun verify(ReadOnlyConnection connection, Account account, String... moreArguments) throws Exception {
        Map<ReadOnlyConnection, Account> accounts = new EnumMap<>(VIOLATING);
        accounts.put(connection, account);
        List<String> arguments = new ArrayList<>(List.of("--spring.profiles.active=" + PROFILE, CONNECTION_OPTION + connection.connectionName));
        arguments.addAll(List.of(moreArguments));

        CommandRun run = launch(command(arguments), environment(accounts));

        assertStayedOffTheNetworkAndTheApplicationDatabase(run);
        return run;
    }

    /** As on ECS (INFRA_RUNBOOK E-6): java -Duser.timezone=UTC -jar forcitis-one.jar, with the probe as the selector provider. */
    static List<String> command(List<String> arguments) {
        List<String> command = new ArrayList<>(List.of(java(), "-Duser.timezone=UTC", "-Xbootclasspath/a:" + PROBE_CLASSES, "--add-exports",
                "java.base/sun.nio.ch=ALL-UNNAMED", "-Djava.nio.channels.spi.SelectorProvider=" + ListeningSocketProbe.class.getName(),
                "-jar", RELEASE_JAR.toString()));
        command.addAll(arguments);
        return command;
    }

    /**
     * The part of one.env the command may read (INFRA_RUNBOOK section 4 variable table): every read-only connection configured, the service's
     * own database, profile, ports and jobs switch. The service's ports are set because one.env sets them and environment variables rank
     * above a profile's properties file.
     */
    static Map<String, String> environment(Map<ReadOnlyConnection, Account> accounts) throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("SPRING_PROFILES_ACTIVE", "dev");
        env.put("SPRING_DATASOURCE_URL", APPLICATION_DATABASE.getJdbcUrl());
        env.put("SPRING_DATASOURCE_USERNAME", APPLICATION_DATABASE.getUsername());
        env.put("SPRING_DATASOURCE_PASSWORD", APPLICATION_DATABASE.getPassword());
        env.put("SERVER_ADDRESS", "127.0.0.1");
        env.put("SERVER_PORT", String.valueOf(freePort()));
        env.put("MANAGEMENT_SERVER_ADDRESS", "127.0.0.1");
        env.put("MANAGEMENT_SERVER_PORT", String.valueOf(freePort()));
        env.put("FORCITIS_JOBS_ENABLED", "true");
        env.put("INTEGRATION_SEEYON_VIEWS_ENABLED", "true");
        env.put("FORCITIS_PLATFORM_INTEGRATION_SEEYON_VIEWS_HEAD_RELATIONSHIP_TYPE_ID", "7001");
        env.put("FORCITIS_PLATFORM_INTEGRATION_SEEYON_VIEWS_REGIONAL_RECEIVABLES_MANAGER_RELATIONSHIP_TYPE_ID", "7002");
        env.put("FORCITIS_PLATFORM_INTEGRATION_SEEYON_VIEWS_DELIVERY_UNIT_YES_ENUM_ID", "9001");
        env.put("FORCITIS_PLATFORM_INTEGRATION_SEEYON_VIEWS_FEE_EARNER_YES_ENUM_ID", "9002");
        env.put("FORCITIS_PLATFORM_INTEGRATION_SEEYON_VIEWS_ROOT_PATH", "00000001");
        env.put("INTEGRATION_LEGACY_ENABLED", "true");
        env.put("FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_DETAIL_URL_TEMPLATE", "https://legacy.example.test/todo/{processInstanceId}/{taskId}");
        accounts.forEach((connection, account) -> {
            env.put(connection.urlVariable, account.url());
            env.put(connection.usernameVariable, account.login());
            env.put(connection.passwordVariable, account.password());
        });
        return env;
    }

    /** Only the given variables and what any process needs: nothing of the developer's or the CI's environment leaks into the command. */
    static CommandRun launch(List<String> command, Map<String, String> environment) throws IOException, InterruptedException {
        Path directory = Files.createTempDirectory("read-only-verification");
        Path out = directory.resolve("stdout.txt");
        Path err = directory.resolve("stderr.txt");
        ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(out.toFile()).redirectError(err.toFile());
        Map<String, String> env = builder.environment();
        Map<String, String> kept = new LinkedHashMap<>();
        for (String name : List.of("PATH", "HOME", "TMPDIR", "LANG")) {
            if (env.containsKey(name)) {
                kept.put(name, env.get(name));
            }
        }
        env.clear();
        env.putAll(kept);
        env.putAll(environment);
        Process process = builder.start();
        boolean ended = process.waitFor(COMMAND_TIME_LIMIT.toSeconds(), TimeUnit.SECONDS);
        if (!ended) {
            process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
        }
        String output = Files.readString(out, StandardCharsets.UTF_8) + "\n" + Files.readString(err, StandardCharsets.UTF_8);
        assertThat(ended).as("the command must end by itself within %s (a command, not a service); output:\n%s", COMMAND_TIME_LIMIT, output).isTrue();
        return new CommandRun(process.exitValue(), output);
    }

    static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /** The packaged application, as handed to ECS; failsafe runs after package. A jar older than the compiled classes is refused, not used. */
    static Path releaseJar() {
        try (Stream<Path> files = Files.list(Path.of("target"))) {
            Path jar = files.filter(p -> p.getFileName().toString().matches("forcitis-one-[0-9][^-]*\\.jar")).max(Comparator.comparing(ReadOnlyVerificationCommandIT::modified))
                    .orElseThrow(() -> new IllegalStateException("no target/forcitis-one-*.jar: the command is run from the packaged application as on ECS; "
                            + "run ./mvnw verify (failsafe runs after package)"));
            FileTime newestClass;
            try (Stream<Path> classes = Files.walk(Path.of("target/classes"))) {
                newestClass = classes.filter(p -> p.toString().endsWith(".class")).map(ReadOnlyVerificationCommandIT::modified).max(Comparator.naturalOrder())
                        .orElseThrow(() -> new IllegalStateException("target/classes holds no class"));
            }
            if (modified(jar).compareTo(newestClass) < 0) {
                throw new IllegalStateException(jar + " is older than target/classes: package again (./mvnw verify) so the jar holds the code under test");
            }
            return jar;
        } catch (IOException e) {
            throw new IllegalStateException("cannot look for the packaged application in target/", e);
        }
    }

    static FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the time of " + path, e);
        }
    }

    /** The probe alone, for -Xbootclasspath/a: the test classes as a whole must not reach the command's class path. */
    static Path probeClasses() {
        try {
            String relative = ListeningSocketProbe.class.getName().replace('.', '/') + ".class";
            Path compiled = Path.of(ListeningSocketProbe.class.getResource("/" + relative).toURI());
            Path root = Files.createTempDirectory("listening-socket-probe");
            Path target = root.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.copy(compiled, target, StandardCopyOption.REPLACE_EXISTING);
            return root;
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("cannot prepare the listening socket probe", e);
        }
    }

    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static String marker() {
        return "mk" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** What the driver tells this login when it connects to the account's URL: the oracle for the error number and SQLState the command prints. */
    static SQLException connectionFailure(Account account) {
        SQLException failure = catchThrowableOfType(SQLException.class,
                () -> DriverManager.getConnection(account.url() + ";loginTimeout=10", account.login(), account.password()).close());
        assertThat((Throwable) failure).as("the fixture: connecting to %s must fail", account.url()).isNotNull();
        return failure;
    }

    /** What SQL Server tells this login when it reads sys.database_permissions: the oracle for the error the command prints. */
    static SQLException catalogRefusal(Account account) throws SQLException {
        try (Connection c = DriverManager.getConnection(account.url() + ";loginTimeout=30", account.login(), account.password());
                Statement s = c.createStatement()) {
            SQLException failure = catchThrowableOfType(SQLException.class, () -> strings(s, "SELECT permission_name FROM sys.database_permissions"));
            assertThat((Throwable) failure).as("the fixture: reading sys.database_permissions must be refused").isNotNull();
            assertThat(failure.getErrorCode()).as("SQL Server: the SELECT permission was denied").isEqualTo(229);
            return failure;
        }
    }

    static Account baselineAccount(ReadOnlyConnection connection) {
        String login = TestMssql.login(ReadOnlyVerificationCommandIT.class, connection.name().toLowerCase(Locale.ROOT) + "_ro_" + LOGINS.incrementAndGet());
        String password = "Ro1!" + UUID.randomUUID();
        try {
            switch (connection) {
                case A8 -> ReadOnlyBaselineFixture.createViewSchemaReader(ReadOnlyVerificationCommandIT::run, connection.database, login, password);
                case LEGACY_NAMES -> ReadOnlyBaselineFixture.createSingleViewReader(ReadOnlyVerificationCommandIT::run, connection.database,
                        "dbo.v_one_legacy_org_short_name", login, password);
                case INTEGRATION_LEGACY_INBOX -> ReadOnlyBaselineFixture.createSingleViewReader(ReadOnlyVerificationCommandIT::run, connection.database,
                        "dbo.v_psa_one_legacy_todo", login, password);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("cannot create the read-only login " + login, e);
        }
        return new Account(connection, login, password, url(connection.database));
    }

    static void assertMatchesTheRdsBaseline(Account account) throws SQLException {
        ReadOnlyBaselineFixture.assertMatchesTheRdsBaseline(account.url(), account.connection().database, account.login(), account.password());
    }

    static List<String> strings(Statement s, String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = s.executeQuery(query)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
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
            ReadOnlyBaselineFixture.createRdsPlatform(ReadOnlyVerificationCommandIT::run);
            run(A8_WORLD, "CREATE SCHEMA " + ReadOnlyBaselineFixture.VIEW_SCHEMA + " AUTHORIZATION dbo",
                    "CREATE TABLE dbo.member_rows (ID bigint PRIMARY KEY, NAME nvarchar(64), LOGIN_NAME nvarchar(64))",
                    "CREATE VIEW " + ReadOnlyBaselineFixture.VIEW_SCHEMA + ".v_org_member AS SELECT ID, NAME, LOGIN_NAME FROM dbo.member_rows");
            run(LEGACY_WORLD,
                    "CREATE TABLE dbo.todo_rows (assignee_user_id bigint, task_id nvarchar(64))",
                    "CREATE TABLE dbo.short_name_rows (id bigint, short_name nvarchar(64))",
                    "CREATE VIEW dbo.v_psa_one_legacy_todo AS SELECT assignee_user_id, task_id FROM dbo.todo_rows",
                    "CREATE VIEW dbo.v_one_legacy_org_short_name AS SELECT id, short_name FROM dbo.short_name_rows");
        } catch (SQLException e) {
            throw new IllegalStateException("cannot prepare the external databases; SQL Server log:\n" + MSSQL.getLogs(), e);
        }
    }
}
