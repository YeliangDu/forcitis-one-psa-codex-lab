package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.OneApplication;
import com.forcitis.one.TestMssql;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.application.GetSyncRun;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.SyncResult;
import com.forcitis.one.organization.workforce.application.SyncRunDetailView;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.application.SourceReferences;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b-0a (D-1a-01; ADR-0037 items 2.4, 3.5, 6.1, 6.3, 8.4; card sections 4 and 14, RULING-R1B0-7): TYPE of dbo.ORG_UNIT is read from the base
 * table next to the frozen org unit view and checked (an Account below the root, or any unit whose TYPE is NULL or neither Department nor
 * Account, rejects the whole run); the start-up check covers the run-time base-table rows of the read-column register as a subset; the person
 * statement does not change. The person flags of dbo.ORG_MEMBER and the two judgements moved to R-1b-0b; their cases wait in
 * src/test/resources/acceptance/platform/integration/r1b0b-pending/A8MemberFlagsCases.md.
 *
 * <p>Black box: a real SQL Server whose views are the DBA script itself (phase1a/SEEYON_VIEWS.sql) over A8-shaped base tables, a read-only
 * login equal to the RDS baseline ({@link ReadOnlyBaselineFixture}), the real application started with SpringApplicationBuilder, and only
 * public entry points: {@link OrgSource}, {@link SyncOrgSnapshot}, {@link GetSyncRun}, {@link OrgDirectory}, the log, the SQL Server plan cache.
 * No type of the change is referenced (none exists before it).
 *
 * <p>Base-table column types follow one-ops evidence 2026-10-15: STATE, IS_DELETED, IS_ENABLE smallint and nullable, ORG_PRINCIPAL.LOGIN_NAME
 * nvarchar(50). ORG_UNIT.TYPE was not reported and is taken as nvarchar; the flags of ORG_UNIT and ORG_MEMBER.IS_INTERNAL as smallint. All
 * three are to be checked against the evidence.
 */
@ExtendWith(OutputCaptureExtension.class)
class A8SourceFlagsIT {

    // ---- Names this test asserts that are still in terminology review (one-glossary); rename here only. ----
    /** Read-column register (ADR-0037 item 6.1), relative to backend/. */
    static final Path READ_REGISTRY = Path.of("architecture/external-read-registry.yaml");
    static final String PURPOSE_RUNTIME_SYNC = "RUNTIME_SYNC";
    static final String PURPOSE_RUNTIME_TODO = "RUNTIME_TODO";
    static final Set<String> RUNTIME_PURPOSES = Set.of(PURPOSE_RUNTIME_SYNC, PURPOSE_RUNTIME_TODO);

    static final Path VIEW_SCRIPT = Path.of("../docs/modernization/phase1a/SEEYON_VIEWS.sql");
    /** The two relationship-type ids are read from the DBA view script itself, so the fixture and the script cannot drift apart. */
    static final long[] RELATIONSHIP_TYPES = relationshipTypesInViewScript();
    static final long HEAD_TYPE = RELATIONSHIP_TYPES[0];
    static final long MANAGER_TYPE = RELATIONSHIP_TYPES[1];
    static final String DELIVERY_YES = "-26825909999140570";
    static final String FEE_EARNER_YES = "-32676477351479120";

    static long[] relationshipTypesInViewScript() {
        try {
            Matcher in = Pattern.compile("OBJECTIVE1_ID\\s+IN\\s*\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)").matcher(Files.readString(VIEW_SCRIPT));
            if (!in.find()) {
                throw new IllegalStateException("no relationship-type list in " + VIEW_SCRIPT);
            }
            return new long[] {Long.parseLong(in.group(1)), Long.parseLong(in.group(2))};
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final String ROOT_PATH = "00000001";
    static final String ROOT_PREFIX = ROOT_PATH + "%";

    /**
     * The person and org unit statements exactly as they were before R-1b-0 (SeeyonViewSql at d3a9f07f), frozen here as the reference: the view
     * part of the statements the adapter sends must return the same rows (card 3.2, C-2).
     */
    static final String MEMBER_VIEW_COLUMNS = "ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, SUPERVISOR_MEMBER_ID, WE_COM_ID, IS_INTERNAL, "
            + "LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID";
    static final String PURE_VIEW_MEMBERS = "SELECT " + MEMBER_VIEW_COLUMNS + " FROM forcitis_one.v_org_member WHERE ID IS NOT NULL "
            + "AND (IS_INTERNAL = 1 OR IS_INTERNAL IS NULL) AND CODE IS NOT NULL AND CODE <> '' AND ORG_UNIT_ID IS NOT NULL "
            + "AND ORG_UNIT_PATH LIKE @PureViewRootPrefix";
    static final String UNIT_VIEW_COLUMNS = "ID, CODE, NAME, SORT_ID, PATH, IS_DELETED, IS_INTERNAL, IS_ENABLE, CATEGORY, DELIVERY_FLAG_ENUM_ID";
    static final String PURE_VIEW_UNITS = "SELECT " + UNIT_VIEW_COLUMNS + " FROM forcitis_one.v_org_unit WHERE PATH LIKE @PureViewRootPrefix";
    static final String DECLARE_PURE_VIEW_PREFIX = "DECLARE @PureViewRootPrefix nvarchar(4000) = N'" + ROOT_PREFIX + "'; ";

    /**
     * One person of the fixture. state, deleted, enabled are the raw smallint values; login null means no ORG_PRINCIPAL row. inScope is the
     * current MEMBER_SCOPE (internal, under the root path).
     */
    record Person(String label, long id, long unit, Integer state, Integer deleted, Integer enabled, String login, String wecom, boolean internal,
                  Long supervisor) {

        String name() {
            return "Kestrel " + label;
        }

        String code() {
            return "E-" + label;
        }

        boolean inScope() {
            return internal && unit != 4;
        }
    }

    // The person flags are not read in R-1b-0a; the people stay so that the pure person statement compares over real rows and R-1b-0b can reuse
    // them (r1b0b-pending/A8MemberFlagsCases.md). Production combinations in the current scope (one-ops 2026-10-15, STATE|IS_DELETED|IS_ENABLE|login): 1|0|1|yes 304, 1|1|0|no 6,
    // 2|0|0|no 943, 2|1|0|no 1; no NULL, no illegal value.
    static final Person P1 = new Person("P1", 4_100_000_001L, 2, 1, 0, 1, "13977770001", "wx-p1", true, null);
    static final Person P1B = new Person("P1B", 4_100_000_002L, 3, 1, 0, 1, "13977770002", "wx-p1b", true, 4_100_000_001L);
    static final Person P2 = new Person("P2", 4_100_000_003L, 2, 1, 1, 0, null, null, true, null);
    static final Person P3 = new Person("P3", 4_100_000_004L, 2, 2, 0, 0, null, null, true, null);
    static final Person P4 = new Person("P4", 4_100_000_005L, 3, 2, 1, 0, null, null, true, null);
    // Constructed disagreements (none in production today, the rule must cover them; one-1a-next-3 2026-10-15).
    static final Person C1 = new Person("C1", 4_100_000_011L, 2, 2, 0, 1, "13977770011", "wx-c1", true, null);
    static final Person C2 = new Person("C2", 4_100_000_012L, 2, 1, 1, 1, "13977770012", "wx-c2", true, null);
    static final Person C3 = new Person("C3", 4_100_000_013L, 3, 1, 0, 0, "13977770013", "wx-c3", true, null);
    static final Person C4 = new Person("C4", 4_100_000_014L, 3, 1, 0, 1, null, null, true, null);
    static final Person C5 = new Person("C5", 4_100_000_015L, 2, 2, 0, 0, "13977770015", "wx-c5", true, null);
    // Outside the current scope: under another root, and an external person.
    static final Person OUTSIDE = new Person("OUT", 4_100_000_090L, 4, 1, 0, 1, "13977770090", "wx-out", true, null);
    static final Person EXTERNAL = new Person("EXT", 4_100_000_091L, 2, 1, 0, 1, "13977770091", "wx-ext", false, null);

    static final List<Person> PEOPLE = List.of(P1, P1B, P2, P3, P4, C1, C2, C3, C4, C5, OUTSIDE, EXTERNAL);

    static final String MAIN = TestMssql.database(A8SourceFlagsIT.class, "a8flags").name();
    static final String SA_PASSWORD = TestMssql.SA_PASSWORD;
    static final AtomicInteger LOGINS = new AtomicInteger();
    static final Map<String, Login> LOGINS_BY_DATABASE = new HashMap<>();

    record Login(String user, String password) {
    }

    static final PostgreSQLContainer POSTGRES = TestPostgres.container()
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static final GenericContainer<?> MSSQL = TestMssql.shared();

    static {
        POSTGRES.start();
        try {
            ReadOnlyBaselineFixture.createRdsPlatform(A8SourceFlagsIT::run);
            createA8Database(MAIN);
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("cannot prepare the A8 database; SQL Server log:\n" + MSSQL.getLogs(), e);
        }
    }

    static ConfigurableApplicationContext main;

    /** Started on first use, inside a test, so the output capture is already in place. */
    static ConfigurableApplicationContext app() {
        if (main == null) {
            main = start(MAIN);
        }
        return main;
    }

    @AfterAll
    static void stop() {
        if (main != null) {
            main.close();
        }
    }

    // ------------------------------------------------------------------ 2

    record Corruption(String name, String column, String table, long rowId, String set, String restore) {
        @Override
        public String toString() {
            return name;
        }
    }

    static final String UNIT_RESTORE = "TYPE = N'Department', NAME = N'Support'";
    static final String ROOT_RESTORE = "TYPE = N'Account', NAME = N'Root'";

    static Stream<Arguments> corruptions() {
        return Stream.of(
                new Corruption("非根单元 TYPE=Group", "TYPE", "dbo.ORG_UNIT", 3, "TYPE = N'Group'", UNIT_RESTORE),
                new Corruption("非根单元 TYPE=NULL", "TYPE", "dbo.ORG_UNIT", 3, "TYPE = NULL", UNIT_RESTORE),
                // RULING-R1B0-2: the root may be Department or Account (C-4), never NULL or anything else.
                new Corruption("根单元 TYPE=Group（RULING-R1B0-2）", "TYPE", "dbo.ORG_UNIT", 1, "TYPE = N'Group'", ROOT_RESTORE),
                new Corruption("根单元 TYPE=NULL（RULING-R1B0-2）", "TYPE", "dbo.ORG_UNIT", 1, "TYPE = NULL", ROOT_RESTORE))
                .map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corruptions")
    @DisplayName("R-1b-0 RULING-R1B0-7 Given 根或非根单元 TYPE 为 NULL 或 Department / Account 以外的值（参数化）When 组织同步 Then 本轮 FAILED、日志有 ALERT、快照不写，失败原因带列名，失败原因与日志不含人员个人信息与 SQL 异常消息")
    void illegalUnitTypeFailsTheWholeRun(Corruption corruption, CapturedOutput output) throws SQLException {
        SyncResult baseline = runSync();
        assertThat(baseline.status()).as("fixture sanity, the clean source syncs: %s", baseline.note()).isEqualTo(SyncRunStatus.SUCCEEDED);
        String original = corruption.rowId() == 1 ? "Root" : "Support";
        assertThat(unitName(corruption.rowId())).as("snapshot before").isEqualTo(original);
        String marker = "Zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int mark = output.getAll().length();
        SyncResult result;
        try {
            run(MAIN, "UPDATE " + corruption.table() + " SET " + corruption.set() + ", NAME = N'" + marker + "' WHERE ID = " + corruption.rowId());
            result = runSync();
        } finally {
            run(MAIN, "UPDATE " + corruption.table() + " SET " + corruption.restore() + " WHERE ID = " + corruption.rowId());
        }
        String log = output.getAll().substring(mark);

        assertThat(result.status()).as("%s: an illegal source value rejects the whole read (C-3); note: %s", corruption, result.note())
                .isEqualTo(SyncRunStatus.FAILED);
        assertThat(log).as("a failed sync raises an ALERT line").containsPattern("ALERT[^\\n]*organization\\.workforce[^\\n]*sync");
        assertThat(unitName(corruption.rowId())).as("the snapshot is not written: the renamed unit would show").isEqualTo(original);
        String reason = String.valueOf(result.note()) + "\n" + getRun(result).run().failureReason();
        assertThat(reason).as("the failure names the column").containsPattern("\\b" + corruption.column() + "\\b");
        for (String text : List.of(reason, log)) {
            assertNoPersonalDataNorSqlMessage(text);
        }
    }

    /** ADR-0037 item 3.5: no A8 personal data, no SQL Server message text. */
    static void assertNoPersonalDataNorSqlMessage(String text) {
        for (Person person : PEOPLE) {
            assertThat(text).as("no personal data of %s", person.label()).doesNotContain(person.name()).doesNotContain(Long.toString(person.id()));
            if (person.login() != null) {
                assertThat(text).doesNotContain(person.login());
            }
        }
        assertThat(text).as("no SQL Server message").doesNotContain("SQLServerException").doesNotContain("Conversion failed");
    }

    // ------------------------------------------------------------------ 7

    @Test
    @DisplayName("R-1b-0 Given 根单元 TYPE=Account 或 Department、其下全为 Department Then 正常；Given 根以下出现 TYPE=Account Then 整轮 FAILED 并 ALERT")
    void accountBelowTheRootFailsTheRun(CapturedOutput output) throws SQLException {
        SyncResult clean = runSync();
        assertThat(clean.status()).as("root Account, all below Department: %s", clean.note()).isEqualTo(SyncRunStatus.SUCCEEDED);

        SyncResult rootDepartment;
        try {
            run(MAIN, "UPDATE dbo.ORG_UNIT SET TYPE = N'Department' WHERE ID = 1");
            rootDepartment = runSync();
        } finally {
            run(MAIN, "UPDATE dbo.ORG_UNIT SET TYPE = N'Account' WHERE ID = 1");
        }
        assertThat(rootDepartment.status()).as("the type of the root unit is not restricted (C-4): %s", rootDepartment.note())
                .isEqualTo(SyncRunStatus.SUCCEEDED);

        int mark = output.getAll().length();
        SyncResult accountBelow;
        try {
            run(MAIN, "UPDATE dbo.ORG_UNIT SET TYPE = N'Account' WHERE ID = 3");
            accountBelow = runSync();
        } finally {
            run(MAIN, "UPDATE dbo.ORG_UNIT SET " + UNIT_RESTORE + " WHERE ID = 3");
        }
        assertThat(accountBelow.status()).as("an Account below the root rejects the whole read: %s", accountBelow.note()).isEqualTo(SyncRunStatus.FAILED);
        String log = output.getAll().substring(mark);
        assertThat(log).containsPattern("ALERT[^\\n]*organization\\.workforce[^\\n]*sync");
        assertNoPersonalDataNorSqlMessage(log);
    }

    // ------------------------------------------------------------------ 8

    /**
     * The decoy is created after the application started: the start-up check refuses any object in the view schema that is not a whitelisted
     * view, so a decoy present at start would only test that check. The run-time re-check before the read looks at permissions, not objects.
     * Both directions: an illegal decoy must not fail a clean dbo, and a legal decoy must not hide an Account below the root in dbo.
     */
    @Test
    @DisplayName("R-1b-0 RULING-R1B0-7 Given A8 用户默认 schema 为 forcitis_one 且其下有同名诱饵 ORG_UNIT When 同步 Then TYPE 取自 dbo.ORG_UNIT：诱饵非法而 dbo 合法时同步成功，诱饵合法而 dbo 根以下有 Account 时整轮 FAILED")
    void unitTypeIsReadByItsDboName() throws SQLException {
        assertThat(app().isActive()).as("started before any decoy exists").isTrue();
        SyncResult illegalDecoy;
        try {
            createUnitDecoy("N'Group'");
            illegalDecoy = runSync();
        } finally {
            run(MAIN, "DROP TABLE IF EXISTS forcitis_one.ORG_UNIT");
        }
        assertThat(illegalDecoy.status()).as("decoy TYPE Group everywhere, dbo clean: %s", illegalDecoy.note()).isEqualTo(SyncRunStatus.SUCCEEDED);

        SyncResult legalDecoy;
        try {
            run(MAIN, "UPDATE dbo.ORG_UNIT SET TYPE = N'Account' WHERE ID = 3");
            createUnitDecoy("N'Department'");
            legalDecoy = runSync();
        } finally {
            run(MAIN, "DROP TABLE IF EXISTS forcitis_one.ORG_UNIT", "UPDATE dbo.ORG_UNIT SET " + UNIT_RESTORE + " WHERE ID = 3");
        }
        assertThat(legalDecoy.status()).as("decoy all Department, dbo has an Account below the root: the dbo value must reject the run; note: %s",
                legalDecoy.note()).isEqualTo(SyncRunStatus.FAILED);
    }

    /** forcitis_one.ORG_UNIT with every unit's ID and the given TYPE; proves the A8 login resolves an unqualified ORG_UNIT to it. */
    static void createUnitDecoy(String type) throws SQLException {
        run(MAIN, "SELECT ID, CAST(" + type + " AS nvarchar(32)) AS TYPE INTO forcitis_one.ORG_UNIT FROM dbo.ORG_UNIT");
        Login reader = LOGINS_BY_DATABASE.get(MAIN);
        try (Connection c = DriverManager.getConnection(url(MAIN) + ";loginTimeout=30", reader.user(), reader.password());
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT TYPE FROM ORG_UNIT WHERE ID = 3")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).as("the trap is armed: for the A8 login an unqualified ORG_UNIT is the decoy")
                    .isEqualTo(type.substring(2, type.length() - 1));
        }
    }

    // ------------------------------------------------------------------ 9

    @Test
    @DisplayName("R-1b-0 Given dbo.ORG_UNIT 缺 TYPE When 启动 Then 拒绝启动并报出缺的表与列")
    void missingUnitTypeRefusesStartup() throws Exception {
        String database = TestMssql.database(A8SourceFlagsIT.class, "a8notype").name();
        createA8Database(database, "ALTER TABLE dbo.ORG_UNIT DROP COLUMN TYPE");

        Throwable failure = startAndClose(database);

        assertThat(failure).as("dbo.ORG_UNIT has no TYPE: the application started anyway").isNotNull();
        assertThat(messages(failure)).as("names the table and the column").containsPattern("ORG_UNIT[^\\n]*\\bTYPE\\b|\\bTYPE\\b[^\\n]*ORG_UNIT");
    }

    @Test
    @DisplayName("R-1b-0 Given 基表多出任意列 When 启动 Then 正常（基表做子集检查）")
    void extraBaseTableColumnsAreAccepted() throws Exception {
        String database = TestMssql.database(A8SourceFlagsIT.class, "a8extracolumns").name();
        createA8Database(database, "ALTER TABLE dbo.ORG_MEMBER ADD R1B0_PROBE_EXTRA nvarchar(8) NULL",
                "ALTER TABLE dbo.ORG_UNIT ADD R1B0_PROBE_EXTRA nvarchar(8) NULL");

        Throwable failure = startAndClose(database);

        assertThat(failure).as("a base table with more columns than registered must start; refused with:\n%s", messages(failure)).isNull();
    }

    @Test
    @DisplayName("R-1b-0 Given 视图多一列 When 启动 Then 仍拒绝（经视图读取的路径仍做相等校验）")
    void extraViewColumnStillRefuses() throws Exception {
        String database = TestMssql.database(A8SourceFlagsIT.class, "a8viewextra").name();
        createA8Database(database, "ALTER VIEW forcitis_one.v_org_post AS SELECT P.ID, P.CODE, P.NAME, P.SORT_ID, P.STATUS, P.IS_DELETED, "
                + "CAST(NULL AS nvarchar(8)) AS R1B0_PROBE_EXTRA FROM dbo.ORG_POST P");

        Throwable failure = startAndClose(database);

        assertThat(failure).as("a view with one more column: the application started anyway").isNotNull();
        assertThat(messages(failure)).containsPattern("(?i)v_org_post[^\\n]*R1B0_PROBE_EXTRA|R1B0_PROBE_EXTRA[^\\n]*v_org_post");
    }

    // ------------------------------------------------------------------ 10

    /**
     * The register is not on the run-time class path (card 3.3), so a migration or reconciliation row cannot reach the start-up check: the
     * check covers the run-time rows only (ADR-0037 item 8.4). The case starts against a database that lacks every column such a row names.
     */
    @Test
    @DisplayName("R-1b-0 Given 登记中一行用途为迁入、指向不存在的列 When 启动 Then 正常启动")
    void migrationRowsDoNotGateStartup() throws Exception {
        assertThat(Files.exists(READ_REGISTRY)).as("the read-column register %s exists (ADR-0037 item 6.1)", READ_REGISTRY).isTrue();
        List<Map<String, String>> rows = registryRows(Files.readAllLines(READ_REGISTRY));
        assertThat(rows).as("register rows in flow style").isNotEmpty();
        assertThat(rows).as("every row carries a purpose").allSatisfy(r -> assertThat(r).containsKey("purpose"));
        ClassLoader loader = OneApplication.class.getClassLoader();
        assertThat(loader.getResource(READ_REGISTRY.getFileName().toString())).as("the register is not a run-time resource").isNull();
        assertThat(loader.getResource(READ_REGISTRY.toString())).isNull();

        List<Map<String, String>> notRuntime = rows.stream()
                .filter(r -> "a8_db".equals(r.get("database")))
                .filter(r -> !RUNTIME_PURPOSES.contains(r.get("purpose").toUpperCase(Locale.ROOT).replace('-', '_')))
                .toList();
        // The injected row of the card: a migration row pointing at a column that does not exist in A8.
        List<Map<String, String>> absent = new ArrayList<>(notRuntime);
        absent.add(Map.of("database", "a8_db", "object", "dbo.ORG_MEMBER", "column", "R1B0_NO_SUCH_COLUMN", "purpose", "MIGRATION"));
        String database = TestMssql.database(A8SourceFlagsIT.class, "a8migration").name();
        createA8Database(database);
        for (Map<String, String> row : absent) {
            String table = row.get("object").replaceFirst("(?i)^\\[?dbo\\]?\\.", "").replace("[", "").replace("]", "");
            assertThat(columnExists(database, table, row.get("column"))).as("fixture: %s.%s must be absent for this case", row.get("object"), row.get("column"))
                    .isFalse();
        }

        Throwable failure = startAndClose(database);

        assertThat(failure).as("rows for migration or reconciliation (%d in the register) gate nothing at start-up; refused with:\n%s", notRuntime.size(),
                messages(failure)).isNull();
    }

    // ------------------------------------------------------------------ 11

    /**
     * The statements the adapter really sends are taken from the SQL Server plan cache after one snapshot read (black box: nothing of the adapter is
     * referenced). The view columns of the new statement, EXCEPT the frozen pure view statement, both ways, must be empty.
     */
    @Test
    @DisplayName("R-1b-0 Given 同一夹具 When 新部门语句（JOIN dbo.ORG_UNIT）与改动前纯视图语句各执行一次 Then 两个方向 EXCEPT 都为 0 行；人员语句本卡不变，与纯视图语句同样等价")
    void newStatementsEqualThePureViewStatements() throws SQLException {
        OrgSource a8 = app().getBean(OrgSource.class);
        run("master", "DBCC FREEPROCCACHE WITH NO_INFOMSGS");
        a8.readSnapshot();
        List<String> sent = cachedStatements(MAIN);

        String members = single(sent, "v_org_member", "person");
        String units = single(sent, "v_org_unit", "org unit");
        assertThat(units).as("the org unit statement reads TYPE from dbo.ORG_UNIT next to the view:\n%s", units)
                .containsPattern("(?i)\\[?dbo\\]?\\s*\\.\\s*\\[?ORG_UNIT\\b");

        assertThat(exceptCount(members, MEMBER_VIEW_COLUMNS, PURE_VIEW_MEMBERS)).as("new person statement EXCEPT pure view statement").isZero();
        assertThat(exceptCount(PURE_VIEW_MEMBERS, MEMBER_VIEW_COLUMNS, members)).as("pure view statement EXCEPT new person statement").isZero();
        assertThat(exceptCount(units, UNIT_VIEW_COLUMNS, PURE_VIEW_UNITS)).as("new org unit statement EXCEPT pure view statement").isZero();
        assertThat(exceptCount(PURE_VIEW_UNITS, UNIT_VIEW_COLUMNS, units)).as("pure view statement EXCEPT new org unit statement").isZero();
        assertThat(executableRowCount(members)).as("new person statement row count equals pure view statement (EXCEPT is set semantics, a JOIN duplicate would hide)")
                .isEqualTo(rowCount(PURE_VIEW_MEMBERS));
        assertThat(executableRowCount(units)).as("new org unit statement row count equals pure view statement (EXCEPT is set semantics, a JOIN duplicate would hide)")
                .isEqualTo(rowCount(PURE_VIEW_UNITS));
        assertThat(rowCount(PURE_VIEW_MEMBERS)).as("fixture sanity: the comparison is over real rows").isEqualTo(PEOPLE.stream().filter(Person::inScope).count());
        assertThat(rowCount(PURE_VIEW_UNITS)).isEqualTo(3);
    }

    // ------------------------------------------------------------------ helpers: sync report

    static SyncResult runSync() {
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            return app().getBean(SyncOrgSnapshot.class).execute(SyncTrigger.MANUAL, false);
        }
    }

    static SyncRunDetailView getRun(SyncResult result) {
        return app().getBean(GetSyncRun.class).execute(result.syncRunId()).orElseThrow();
    }

    static String unitName(long sourceUnitId) {
        UUID ownId = app().getBean(SourceReferences.class)
                .findOwnId(ExternalSource.ORG_SOURCE, SourceObjectKind.ORG_UNIT, sourceUnitId)
                .orElseThrow(() -> new AssertionError("one confirmed reference of source unit " + sourceUnitId));
        return app().getBean(OrgDirectory.class).findOrgUnit(new OrgUnitId(ownId)).orElseThrow().name();
    }

    // ------------------------------------------------------------------ helpers: statements

    static List<String> cachedStatements(String database) throws SQLException {
        List<String> texts = new ArrayList<>();
        try (Connection c = admin("master"); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT DISTINCT t.text FROM sys.dm_exec_cached_plans cp CROSS APPLY sys.dm_exec_sql_text(cp.plan_handle) t "
                        + "CROSS APPLY sys.dm_exec_plan_attributes(cp.plan_handle) a "
                        + "WHERE a.attribute = 'dbid' AND CONVERT(int, a.value) = DB_ID(N'" + database + "') "
                        + "AND cp.cacheobjtype = 'Compiled Plan' AND cp.objtype IN ('Prepared', 'Adhoc') "
                        + "AND t.text NOT LIKE N'%dm[_]exec[_]%' AND t.text NOT LIKE N'%COUNT(%'")) {
            while (rs.next()) {
                texts.add(rs.getString(1));
            }
        }
        return texts;
    }

    static String single(List<String> sent, String view, String what) {
        List<String> matching = sent.stream().filter(t -> t.toLowerCase(Locale.ROOT).contains(view)).toList();
        assertThat(matching).as("the adapter sent exactly one %s statement in one snapshot read; plan cache:\n%s", what, String.join("\n---\n", sent))
                .hasSize(1);
        return matching.get(0);
    }

    /** A prepared statement as cached: "(@P0 nvarchar(4000))SELECT ...". Each string parameter is the root path prefix (the only one in a bulk read). */
    static String executable(String cached) {
        String text = cached.trim();
        if (!text.startsWith("(@")) {
            return text;
        }
        int depth = 0;
        int end = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                end = i;
                break;
            }
        }
        assertThat(end).as("parameter list of %s", text).isPositive();
        StringBuilder declare = new StringBuilder();
        for (String parameter : text.substring(1, end).split(",(?=\\s*@)")) {
            String[] parts = parameter.trim().split("\\s+", 2);
            assertThat(parts[1].toLowerCase(Locale.ROOT)).as("a bulk read takes only the root path prefix: %s", parameter).contains("char");
            declare.append("DECLARE ").append(parts[0]).append(' ').append(parts[1]).append(" = N'").append(ROOT_PREFIX).append("'; ");
        }
        return declare + text.substring(end + 1).replaceFirst("(?is)\\s+ORDER\\s+BY\\s+[\\w\\s.,\\[\\]]+$", "");
    }

    static int exceptCount(String left, String columns, String right) throws SQLException {
        String l = executable(left);
        String r = executable(right);
        String declarations = declarations(l) + declarations(r) + DECLARE_PURE_VIEW_PREFIX;
        String sql = declarations + "SELECT COUNT(*) FROM (SELECT " + columns + " FROM (" + body(l) + ") AS l EXCEPT SELECT " + columns + " FROM ("
                + body(r) + ") AS r) AS d";
        try (Connection c = admin(MAIN); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static long executableRowCount(String statement) throws SQLException {
        String e = executable(statement);
        try (Connection c = admin(MAIN); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(declarations(e) + DECLARE_PURE_VIEW_PREFIX + "SELECT COUNT(*) FROM (" + body(e) + ") AS x")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static long rowCount(String statement) throws SQLException {
        try (Connection c = admin(MAIN); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(DECLARE_PURE_VIEW_PREFIX + "SELECT COUNT(*) FROM (" + statement + ") AS x")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static String declarations(String executable) {
        Matcher m = Pattern.compile("^((?:DECLARE [^;]+; )*)").matcher(executable);
        return m.find() ? m.group(1) : "";
    }

    static String body(String executable) {
        return executable.substring(declarations(executable).length());
    }

    // ------------------------------------------------------------------ helpers: register

    static final Pattern FIELD = Pattern.compile("(\\w+)\\s*:\\s*([^,}]+)");

    /** Flow style, one row per line (card 3.3): { database: ..., object: ..., column: ..., purpose: ... }. */
    static List<Map<String, String>> registryRows(List<String> lines) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || !trimmed.contains("{") || !trimmed.contains("column")) {
                continue;
            }
            Map<String, String> row = new HashMap<>();
            Matcher m = FIELD.matcher(trimmed.substring(trimmed.indexOf('{')));
            while (m.find()) {
                row.put(m.group(1), m.group(2).trim().replaceAll("^['\"]|['\"]$", ""));
            }
            rows.add(row);
        }
        return rows;
    }

    static boolean columnExists(String database, String table, String column) throws SQLException {
        try (Connection c = admin(database); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = N'"
                        + table.replace("'", "''") + "' AND COLUMN_NAME = N'" + column.replace("'", "''") + "'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    // ------------------------------------------------------------------ helpers: application

    static Throwable startAndClose(String database) {
        return catchThrowable(() -> {
            try (ConfigurableApplicationContext context = start(database)) {
                assertThat(context.isActive()).isTrue();
            }
        });
    }

    /** Command-line arguments: they rank above application.properties, which switches every external connection off by default. */
    static ConfigurableApplicationContext start(String database) {
        Login reader = LOGINS_BY_DATABASE.get(database);
        String views = "--forcitis.platform.integration.seeyon.views.";
        List<String> args = List.of(
                "--server.port=0",
                "--server.address=127.0.0.1",
                "--spring.main.banner-mode=off",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--flowable.async-executor-activate=false",
                "--forcitis.platform.approval.deadline.enabled=false",
                "--forcitis.platform.integration.legacy.enabled=false",
                views + "enabled=true",
                views + "jdbc-url=" + url(database), views + "username=" + reader.user(), views + "password=" + reader.password(),
                views + "head-relationship-type-id=" + HEAD_TYPE, views + "regional-receivables-manager-relationship-type-id=" + MANAGER_TYPE,
                views + "delivery-unit-yes-enum-id=" + DELIVERY_YES, views + "fee-earner-yes-enum-id=" + FEE_EARNER_YES,
                views + "root-path=" + ROOT_PATH);
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

    // ------------------------------------------------------------------ helpers: SQL Server fixture

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

    /** A8-shaped base tables, the DBA view script as is, then the variant, then a baseline read-only login checked against the RDS export. */
    static void createA8Database(String database, String... variant) throws SQLException, IOException {
        run(database,
                "CREATE TABLE dbo.ORG_UNIT (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, PATH nvarchar(200), "
                        + "TYPE nvarchar(32) NULL, IS_DELETED smallint, IS_INTERNAL smallint, IS_ENABLE smallint)",
                "CREATE TABLE dbo.ORG_MEMBER (ID bigint PRIMARY KEY, NAME nvarchar(64), CODE nvarchar(32), ORG_DEPARTMENT_ID bigint, ORG_POST_ID bigint, "
                        + "EXT_ATTR_37 nvarchar(64), EXT_ATTR_21 date, we_com_id nvarchar(64), IS_INTERNAL smallint, "
                        + "STATE smallint NULL, IS_DELETED smallint NULL, IS_ENABLE smallint NULL)",
                "CREATE TABLE dbo.ORG_PRINCIPAL (ID bigint IDENTITY PRIMARY KEY, MEMBER_ID bigint, LOGIN_NAME nvarchar(50) NULL)",
                "CREATE TABLE dbo.ORG_POST (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, STATUS int, IS_DELETED smallint)",
                "CREATE TABLE dbo.ORG_RELATIONSHIP (ID bigint IDENTITY PRIMARY KEY, SOURCE_ID bigint, OBJECTIVE0_ID bigint, OBJECTIVE1_ID bigint)",
                "CREATE TABLE dbo.ADDRESSBOOK (ID bigint IDENTITY PRIMARY KEY, MEMBER_ID bigint, TYPE nvarchar(16), EXT_ATTR_32 nvarchar(32), EXT_ATTR_33 bigint)",
                "CREATE TABLE dbo.CTP_ENUM (ID bigint PRIMARY KEY, ENUMNAME nvarchar(64))",
                "CREATE TABLE dbo.CTP_ENUM_ITEM (ID bigint PRIMARY KEY, STATE int, REF_ENUMID bigint, SHOWVALUE nvarchar(32))",
                "CREATE TABLE dbo.formmain_0307 (ID bigint IDENTITY PRIMARY KEY, field0217 nvarchar(32), field0234 nvarchar(128), field0284 nvarchar(8), FIELD0244 bigint)");
        List<String> seed = new ArrayList<>(List.of(
                "INSERT dbo.ORG_UNIT VALUES (1, 'C1', N'Root', 1, '00000001', N'Account', 0, 1, 1), "
                        + "(2, 'C2', N'Delivery', 2, '000000010001', N'Department', 0, 1, 1), "
                        + "(3, 'C3', N'Support', 3, '000000010002', N'Department', 0, 1, 1), "
                        + "(4, 'C4', N'Other company', 4, '00000002', N'Account', 0, 1, 1)",
                "INSERT dbo.CTP_ENUM VALUES (500, N'总部/区域')",
                "INSERT dbo.CTP_ENUM_ITEM VALUES (601, 1, 500, N'总部'), (602, 1, 500, N'区域')",
                "INSERT dbo.ADDRESSBOOK (MEMBER_ID, TYPE, EXT_ATTR_32, EXT_ATTR_33) VALUES (1, 'Department', NULL, 601), (2, 'Department', '" + DELIVERY_YES + "', 602)",
                "INSERT dbo.ORG_POST VALUES (100, 'P100', N'Consultant', 1, 0, 0)",
                "INSERT dbo.ORG_RELATIONSHIP (SOURCE_ID, OBJECTIVE0_ID, OBJECTIVE1_ID) VALUES (" + P1.id() + ", 2, " + HEAD_TYPE + "), (" + P1.id() + ", 2, "
                        + MANAGER_TYPE + ")",
                "INSERT dbo.formmain_0307 (field0217, field0234, field0284, FIELD0244) VALUES ('" + P1.code() + "', 'p1@example.test', N'男', " + FEE_EARNER_YES + ")"));
        for (Person p : PEOPLE) {
            seed.addAll(personRows(p));
        }
        run(database, seed.toArray(String[]::new));
        applyViewScript(database);
        if (variant.length > 0) {
            run(database, variant);
        }
        String login = TestMssql.login(A8SourceFlagsIT.class, "flags_ro_" + LOGINS.incrementAndGet());
        String password = "Ro1!" + UUID.randomUUID();
        ReadOnlyBaselineFixture.createViewSchemaReader(A8SourceFlagsIT::run, database, login, password);
        ReadOnlyBaselineFixture.assertMatchesTheRdsBaseline(url(database), database, login, password);
        LOGINS_BY_DATABASE.put(database, new Login(login, password));
    }

    static List<String> personRows(Person p) {
        List<String> rows = new ArrayList<>();
        rows.add("INSERT dbo.ORG_MEMBER (ID, NAME, CODE, ORG_DEPARTMENT_ID, ORG_POST_ID, EXT_ATTR_37, EXT_ATTR_21, we_com_id, IS_INTERNAL, STATE, IS_DELETED, IS_ENABLE) "
                + "VALUES (" + p.id() + ", N'" + p.name() + "', '" + p.code() + "', " + p.unit() + ", 100, "
                + (p.supervisor() == null ? "NULL" : "'" + p.supervisor() + "'") + ", '1990-01-01', "
                + (p.wecom() == null ? "NULL" : "'" + p.wecom() + "'") + ", " + (p.internal() ? 1 : 0) + ", "
                + p.state() + ", " + p.deleted() + ", " + p.enabled() + ")");
        if (p.login() != null) {
            rows.add("INSERT dbo.ORG_PRINCIPAL (MEMBER_ID, LOGIN_NAME) VALUES (" + p.id() + ", N'" + p.login() + "')");
        }
        return rows;
    }

    /** The schema and views blocks of the DBA script, batch by batch, verbatim. */
    static void applyViewScript(String database) throws SQLException, IOException {
        String script = Files.readString(VIEW_SCRIPT);
        for (String block : List.of("schema", "views")) {
            String start = "-- ==== BLOCK: " + block + " ====";
            int from = script.indexOf(start);
            assertThat(from).as("block %s of %s", block, VIEW_SCRIPT).isNotNegative();
            int to = script.indexOf("-- ==== BLOCK:", from + start.length());
            String body = script.substring(from + start.length(), to < 0 ? script.length() : to);
            for (String batch : body.split("(?m)^GO\\s*$")) {
                if (!batch.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "").isBlank()) {
                    run(database, batch);
                }
            }
        }
    }
}
