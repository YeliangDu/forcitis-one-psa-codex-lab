package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.OneApplication;
import com.forcitis.one.TestImages;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ExternalSourceBrokenException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.application.GetSyncRun;
import com.forcitis.one.organization.workforce.application.SyncDiffView;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.SyncResult;
import com.forcitis.one.organization.workforce.application.SyncRunDetailView;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourcePerson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b-0 (D-1a-01; ADR-0037 items 2.4, 3.5, 6.3, 8.4; card section 4): the A8 flag columns STATE, IS_DELETED, IS_ENABLE of dbo.ORG_MEMBER and
 * TYPE of dbo.ORG_UNIT are read from the base tables next to the frozen views. They feed the sync report only; who is employed, the sync
 * scope and the shrink guard stay as they are (RULE-363 by login name, users U-1 / U-2 "keep as is, switch with DA-3").
 *
 * <p>Black box: a real SQL Server whose views are the DBA script itself (phase1a/SEEYON_VIEWS.sql) over A8-shaped base tables, a read-only
 * login equal to the RDS baseline ({@link ReadOnlyBaselineFixture}), the real application started with SpringApplicationBuilder, and only
 * public entry points: {@link OrgSource}, {@link SyncOrgSnapshot}, {@link GetSyncRun}, {@link OrgDirectory}, the log, the SQL Server plan cache.
 * The new source states are not referenced as types (they do not exist before the change): a {@link SourcePerson} prints its components, and
 * the enum constant names below are what the card fixes.
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
    /** The source employment state (STATE) disagrees with the login-name rule. */
    static final String ISSUE_EMPLOYMENT_DISAGREES = "EMPLOYMENT_SOURCE_DISAGREES";
    /** Deleted or disabled in the source, yet employed by the login-name rule. */
    static final String ISSUE_INACTIVE_RECORD_EMPLOYED = "INACTIVE_SOURCE_RECORD_EMPLOYED";
    /** Constants of the new SourceEmploymentState (card 3.1: 1 -> EMPLOYED, 2 -> SEPARATED). */
    static final String SOURCE_EMPLOYED = "EMPLOYED";
    static final String SOURCE_SEPARATED = "SEPARATED";
    // ---- Existing names (SourceRecordState). ----
    static final String RECORD_ACTIVE = "ACTIVE";
    static final String RECORD_DISABLED = "DISABLED";
    static final String RECORD_DELETED = "DELETED";

    static final Pattern EMPLOYMENT_COMPONENT = Pattern.compile("=(" + SOURCE_EMPLOYED + "|" + SOURCE_SEPARATED + ")[,)]");
    static final Pattern RECORD_COMPONENT = Pattern.compile("=(" + RECORD_ACTIVE + "|" + RECORD_DISABLED + "|" + RECORD_DELETED + ")[,)]");

    static final Path VIEW_SCRIPT = Path.of("../docs/modernization/phase1a/SEEYON_VIEWS.sql");
    static final long HEAD_TYPE = -1726439953579618944L;
    static final long MANAGER_TYPE = -8187907605834545337L;
    static final String DELIVERY_YES = "-2682590999914657055";
    static final String FEE_EARNER_YES = "-3267712510147735998";
    static final String ROOT_PATH = "00000001";
    static final String ROOT_PREFIX = ROOT_PATH + "%";

    /**
     * The person statement exactly as it was before R-1b-0 (SeeyonViewSql.MEMBERS at d3a9f07f), frozen here as the reference: the view part of
     * the new statement must return the same rows (card 3.2, C-2).
     */
    static final String MEMBER_VIEW_COLUMNS = "ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, SUPERVISOR_MEMBER_ID, WE_COM_ID, IS_INTERNAL, "
            + "LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID";
    static final String PURE_VIEW_MEMBERS = "SELECT " + MEMBER_VIEW_COLUMNS + " FROM forcitis_one.v_org_member WHERE ID IS NOT NULL "
            + "AND (IS_INTERNAL = 1 OR IS_INTERNAL IS NULL) AND CODE IS NOT NULL AND CODE <> '' AND ORG_UNIT_ID IS NOT NULL "
            + "AND ORG_UNIT_PATH LIKE @PureViewRootPrefix";
    static final String UNIT_VIEW_COLUMNS = "ID, CODE, NAME, SORT_ID, PATH, IS_DELETED, IS_INTERNAL, IS_ENABLE, CATEGORY, DELIVERY_FLAG_ENUM_ID";
    static final String PURE_VIEW_UNITS = "SELECT " + UNIT_VIEW_COLUMNS + " FROM forcitis_one.v_org_unit WHERE PATH LIKE @PureViewRootPrefix";
    static final String DECLARE_PURE_VIEW_PREFIX = "DECLARE @PureViewRootPrefix nvarchar(4000) = N'" + ROOT_PREFIX + "'; ";

    /** Issue kinds before R-1b-0; a sync report kind outside this set and the two above would be a third new kind. */
    static final Set<String> ISSUE_KINDS_BEFORE = Set.of("NULL_FLAG", "DUPLICATE_UNIT", "MULTIPLE_HEADS", "MULTIPLE_REGIONAL_MANAGERS",
            "PARENT_NOT_FOUND", "HEAD_NOT_FOUND", "REGIONAL_MANAGER_NOT_FOUND", "UNKNOWN_CATEGORY", "ISOLATED_MISSING_ORG_UNIT",
            "ISOLATED_MISSING_POSITION", "DUPLICATE_EMPLOYEE", "DUPLICATE_WECOM_USER_ID", "SUPERVISOR_IS_SELF", "SUPERVISOR_NOT_FOUND",
            "SUPERVISOR_SEPARATED", "SUPERVISOR_CYCLE");

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

        String expectedEmployment() {
            return state == 1 ? SOURCE_EMPLOYED : SOURCE_SEPARATED;
        }

        String expectedRecord() {
            return deleted == 1 ? RECORD_DELETED : enabled == 0 ? RECORD_DISABLED : RECORD_ACTIVE;
        }

        EmployeeId employeeId() {
            return new EmployeeId(id);
        }

        String subject() {
            return "employee:" + id;
        }
    }

    // Production combinations in the current scope (one-ops 2026-10-15, STATE|IS_DELETED|IS_ENABLE|login): 1|0|1|yes 304, 1|1|0|no 6,
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

    /** RULING-R1B0-5: not in the fixture at start (so not in the snapshot); inserted with an illegal STATE only for the login case. */
    static final Person LOGIN_VICTIM = new Person("L1", 4_100_000_021L, 2, 3, 0, 1, "13977770021", "wx-l1", true, null);

    /** Control of RULING-R1B0-5: the same login-miss path with a legal row. */
    static final Person LOGIN_CONTROL = new Person("L0", 4_100_000_020L, 2, 1, 0, 1, "13977770020", "wx-l0", true, null);

    static final List<Person> PEOPLE = List.of(P1, P1B, P2, P3, P4, C1, C2, C3, C4, C5, OUTSIDE, EXTERNAL);
    static final List<Person> PRODUCTION_COMBINATIONS = List.of(P1, P1B, P2, P3, P4);

    static final String MAIN = "a8flags";
    static final String SA_PASSWORD = "Aa1!" + UUID.randomUUID();
    static final AtomicInteger LOGINS = new AtomicInteger();
    static final Map<String, Login> LOGINS_BY_DATABASE = new HashMap<>();

    record Login(String user, String password) {
    }

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestImages.startingWith("postgres:"))
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static final GenericContainer<?> MSSQL = new GenericContainer<>(TestImages.startingWith(TestImages.MSSQL_PREFIX))
            .withEnv("ACCEPT_EULA", "Y").withEnv("MSSQL_SA_PASSWORD", SA_PASSWORD).withExposedPorts(1433)
            .withStartupTimeout(Duration.ofMinutes(5))
            .waitingFor(Wait.forLogMessage(".*SQL Server is now ready for client connections.*", 1));

    static {
        POSTGRES.start();
        MSSQL.start();
        try {
            waitForAdministratorLogin();
            ReadOnlyBaselineFixture.createRdsPlatform(A8SourceFlagsIT::run);
            createA8Database(MAIN);
        } catch (SQLException | IOException | ConditionTimeoutException e) {
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

    // ------------------------------------------------------------------ 1

    @Test
    @DisplayName("R-1b-0 Given 基表人员按生产四种 STATE/IS_DELETED/IS_ENABLE 组合（另加构造的分歧人员）When 读组织快照 Then 每人带来源在职状态与来源记录状态，同步范围人员集合与改动前纯视图语句完全相同")
    void snapshotCarriesSourceStatesAndKeepsTheScope() throws SQLException {
        assertThat(productionCombinationsInFixture()).as("the fixture holds the four production combinations of the current scope")
                .containsExactlyInAnyOrder("1|0|1|yes", "1|1|0|no", "2|0|0|no", "2|1|0|no");

        SourceOrgSnapshot snapshot = app().getBean(OrgSource.class).readSnapshot();

        Map<Long, String> pureView = pureViewLoginsById(MAIN);
        assertThat(pureView.keySet()).as("fixture sanity: the pure view statement sees exactly the in-scope people")
                .containsExactlyInAnyOrderElementsOf(PEOPLE.stream().filter(Person::inScope).map(Person::id).toList());
        Map<Long, String> read = new HashMap<>();
        snapshot.staff().forEach(p -> read.put(p.personId().value(), p.loginName()));
        assertThat(read).as("same people, same login names as the statement before R-1b-0 (MEMBER_SCOPE unchanged)").isEqualTo(pureView);

        for (Person person : PEOPLE.stream().filter(Person::inScope).toList()) {
            SourcePerson read1 = snapshot.staff().stream().filter(p -> p.personId().value() == person.id()).findFirst().orElseThrow();
            String printed = read1.toString();
            assertThat(components(EMPLOYMENT_COMPONENT, printed)).as("source employment state of %s (STATE=%s): %s", person.label(), person.state(), printed)
                    .containsExactly(person.expectedEmployment());
            assertThat(components(RECORD_COMPONENT, printed))
                    .as("source record state of %s (IS_DELETED=%s, IS_ENABLE=%s): %s", person.label(), person.deleted(), person.enabled(), printed)
                    .containsExactly(person.expectedRecord());
        }
    }

    // ------------------------------------------------------------------ 2

    record Corruption(String name, String column, String table, long rowId, String set, String restore) {
        @Override
        public String toString() {
            return name;
        }
    }

    static final String MEMBER_RESTORE = "STATE = 1, IS_DELETED = 0, IS_ENABLE = 1, NAME = N'" + P1B.name() + "'";
    static final String UNIT_RESTORE = "TYPE = N'Department', NAME = N'Support'";
    static final String ROOT_RESTORE = "TYPE = N'Account', NAME = N'Root'";

    static Stream<Arguments> corruptions() {
        return Stream.of(
                new Corruption("STATE=3", "STATE", "dbo.ORG_MEMBER", P1B.id(), "STATE = 3", MEMBER_RESTORE),
                new Corruption("STATE=0", "STATE", "dbo.ORG_MEMBER", P1B.id(), "STATE = 0", MEMBER_RESTORE),
                new Corruption("STATE=NULL", "STATE", "dbo.ORG_MEMBER", P1B.id(), "STATE = NULL", MEMBER_RESTORE),
                new Corruption("IS_DELETED=2", "IS_DELETED", "dbo.ORG_MEMBER", P1B.id(), "IS_DELETED = 2", MEMBER_RESTORE),
                new Corruption("IS_DELETED=NULL", "IS_DELETED", "dbo.ORG_MEMBER", P1B.id(), "IS_DELETED = NULL", MEMBER_RESTORE),
                new Corruption("IS_ENABLE=2", "IS_ENABLE", "dbo.ORG_MEMBER", P1B.id(), "IS_ENABLE = 2", MEMBER_RESTORE),
                new Corruption("IS_ENABLE=NULL", "IS_ENABLE", "dbo.ORG_MEMBER", P1B.id(), "IS_ENABLE = NULL", MEMBER_RESTORE),
                new Corruption("非根单元 TYPE=Group", "TYPE", "dbo.ORG_UNIT", 3, "TYPE = N'Group'", UNIT_RESTORE),
                new Corruption("非根单元 TYPE=NULL", "TYPE", "dbo.ORG_UNIT", 3, "TYPE = NULL", UNIT_RESTORE),
                // RULING-R1B0-2: the root may be Department or Account (C-4), never NULL or anything else.
                new Corruption("根单元 TYPE=Group（RULING-R1B0-2）", "TYPE", "dbo.ORG_UNIT", 1, "TYPE = N'Group'", ROOT_RESTORE),
                new Corruption("根单元 TYPE=NULL（RULING-R1B0-2）", "TYPE", "dbo.ORG_UNIT", 1, "TYPE = NULL", ROOT_RESTORE))
                .map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corruptions")
    @DisplayName("R-1b-0 Given 某人 STATE 为 3、0 或 NULL（参数化；IS_* 为 2 或 NULL；TYPE 为其他值或 NULL）When 组织同步 Then 本轮 FAILED、日志有 ALERT、快照不写，失败原因与日志不含该人 ID、姓名、登录名与 SQL 异常消息")
    void illegalFlagFailsTheWholeRun(Corruption corruption, CapturedOutput output) throws SQLException {
        SyncResult baseline = runSync();
        assertThat(baseline.status()).as("fixture sanity, the clean source syncs: %s", baseline.note()).isEqualTo(SyncRunStatus.SUCCEEDED);
        boolean member = corruption.table().equals("dbo.ORG_MEMBER");
        String original = member ? P1B.name() : corruption.rowId() == 1 ? "Root" : "Support";
        assertThat(member ? employee(P1B).name() : unitName(corruption.rowId())).as("snapshot before").isEqualTo(original);
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
        assertThat(member ? employee(P1B).name() : unitName(corruption.rowId())).as("the snapshot is not written: the renamed row would show")
                .isEqualTo(original);
        String reason = String.valueOf(result.note()) + "\n" + getRun(result).run().failureReason();
        assertThat(reason).as("the failure names the column").containsPattern("\\b" + corruption.column() + "\\b");
        for (String text : List.of(reason, log)) {
            assertThat(text).as("no personal data, no SQL Server message (ADR-0037 item 3.5)")
                    .doesNotContain(marker).doesNotContain(Long.toString(P1B.id())).doesNotContain(P1B.login())
                    .doesNotContain("SQLServerException").doesNotContain("Conversion failed");
        }
    }

    // ------------------------------------------------------------------ 3

    @Test
    @DisplayName("R-1b-0 Given 企业微信 userid 命中一个 STATE 非法的人 When 单人查找 Then 抛来源数据损坏，不返回也不静默跳过")
    void singleLookupOfAnIllegalPersonIsBroken() throws SQLException {
        OrgSource a8 = app().getBean(OrgSource.class);
        assertThat(a8.findStaffByWecomUserId(new WecomUserId(P1B.wecom()))).as("fixture sanity: found while clean").hasSize(1);

        Throwable byWecom;
        Throwable byLogin;
        try {
            run(MAIN, "UPDATE dbo.ORG_MEMBER SET STATE = 3 WHERE ID = " + P1B.id());
            byWecom = catchThrowable(() -> a8.findStaffByWecomUserId(new WecomUserId(P1B.wecom())));
            byLogin = catchThrowable(() -> a8.findStaffByLoginName(P1B.login()));
        } finally {
            run(MAIN, "UPDATE dbo.ORG_MEMBER SET " + MEMBER_RESTORE + " WHERE ID = " + P1B.id());
        }

        for (Throwable failure : java.util.Arrays.asList(byWecom, byLogin)) {
            assertThat(failure).as("the same mapper as the bulk read: an illegal STATE is a broken source, not an empty or partial answer")
                    .isInstanceOf(ExternalSourceBrokenException.class);
            assertThat(messages(failure)).doesNotContain(Long.toString(P1B.id())).doesNotContain(P1B.name()).doesNotContain(P1B.login())
                    .doesNotContain(P1B.wecom()).doesNotContain("SQLServerException");
        }
    }

    // ------------------------------------------------------------------ 4

    @Test
    @DisplayName("R-1b-0 Given 有登录名但 STATE=2（或 IS_DELETED=1、或 IS_ENABLE=0）When 同步 Then 仍按登录名判为在职（RULE-363 现行口径），同步报告多一条来源状态问题，缩水保护计数不变")
    void loginStillDecidesAndTheReportShowsTheDisagreement() throws SQLException {
        SyncResult result = runSync();
        assertThat(result.status()).as(result.note()).isEqualTo(SyncRunStatus.SUCCEEDED);
        SyncRunDetailView report = getRun(result);

        for (Person person : List.of(C1, C2, C3, C5)) {
            assertThat(employee(person).employmentStatus()).as("%s has a login name: employed (RULE-363)", person.label())
                    .isEqualTo(EmploymentStatus.EMPLOYED);
        }
        assertThat(issueKinds(report, C1)).as("C1: login, STATE=2").containsExactly(ISSUE_EMPLOYMENT_DISAGREES);
        assertThat(issueKinds(report, C2)).as("C2: login, IS_DELETED=1").containsExactly(ISSUE_INACTIVE_RECORD_EMPLOYED);
        assertThat(issueKinds(report, C3)).as("C3: login, IS_ENABLE=0").containsExactly(ISSUE_INACTIVE_RECORD_EMPLOYED);
        assertThat(issueKinds(report, C5)).as("C5: login, STATE=2 and disabled: both readers, one issue each")
                .containsExactlyInAnyOrder(ISSUE_EMPLOYMENT_DISAGREES, ISSUE_INACTIVE_RECORD_EMPLOYED);

        assertThat(report.run().incomingEmployedCount()).as("shrink guard numerator: people with a login name in the unchanged scope")
                .isEqualTo(pureViewLoginsById(MAIN).values().stream().filter(l -> l != null && !l.isBlank()).count());
        assertThat(report.run().incomingEmployedCount()).as("P1, P1B, C1, C2, C3, C5").isEqualTo(6);
        assertNoPersonalDataInSourceStateIssues(report);
        assertNoThirdNewKind(report);
    }

    // ------------------------------------------------------------------ 5

    @Test
    @DisplayName("R-1b-0 Given 无登录名但 STATE=1 且有效 When 同步 Then 判为离职（口径不变）并记一条来源状态问题；Given 两者一致 Then 无此问题")
    void noLoginIsSeparatedAndTheReportShowsTheDisagreement() throws SQLException {
        SyncResult result = runSync();
        assertThat(result.status()).as(result.note()).isEqualTo(SyncRunStatus.SUCCEEDED);
        SyncRunDetailView report = getRun(result);

        assertThat(employee(C4).employmentStatus()).as("C4 has no login name: separated (RULE-363)").isEqualTo(EmploymentStatus.SEPARATED);
        assertThat(issueKinds(report, C4)).as("C4: no login, STATE=1, active").containsExactly(ISSUE_EMPLOYMENT_DISAGREES);
        assertThat(issueKinds(report, P2)).as("P2 (production 1|1|0, no login): STATE=1 disagrees with the login rule; deleted and not employed is no second issue")
                .containsExactly(ISSUE_EMPLOYMENT_DISAGREES);
        for (Person agreeing : List.of(P1, P1B, P3, P4)) {
            assertThat(issueKinds(report, agreeing)).as("%s: source state and login rule agree", agreeing.label()).isEmpty();
        }
        assertNoPersonalDataInSourceStateIssues(report);
        assertNoThirdNewKind(report);
    }

    // ------------------------------------------------------------------ 6

    @Test
    @DisplayName("R-1b-0 Given 已删除或停用的人员落在现行范围内 When 同步 Then 仍进入快照（MEMBER_SCOPE 不变）")
    void deletedAndDisabledPeopleStayInTheSnapshot() throws SQLException {
        SyncResult result = runSync();
        assertThat(result.status()).as(result.note()).isEqualTo(SyncRunStatus.SUCCEEDED);

        for (Person person : List.of(P2, P3, P4, C2, C3, C5)) {
            EmployeeProfile profile = employee(person);
            assertThat(profile.name()).as("%s is in the snapshot", person.label()).isEqualTo(person.name());
            assertThat(profile.employmentStatus()).as("%s: by login name only", person.label())
                    .isEqualTo(person.login() == null ? EmploymentStatus.SEPARATED : EmploymentStatus.EMPLOYED);
        }
        assertThat(app().getBean(OrgSource.class).readSnapshot().staff()).extracting(p -> p.personId().value())
                .contains(P2.id(), P3.id(), P4.id(), C2.id(), C3.id(), C5.id());
    }

    // ------------------------------------------------------------------ 7

    @Test
    @DisplayName("R-1b-0 Given 根单元 TYPE=Account、其下全为 Department Then 正常；Given 根以下出现 TYPE=Account Then 整轮 FAILED 并 ALERT")
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
        assertThat(output.getAll().substring(mark)).containsPattern("ALERT[^\\n]*organization\\.workforce[^\\n]*sync");
    }

    // ------------------------------------------------------------------ 8

    /**
     * The decoys are created after the application started: the start-up check refuses any object in the view schema that is not a whitelisted
     * view, so a decoy present at start would only test that check. The run-time re-check before the read looks at permissions, not objects.
     */
    @Test
    @DisplayName("R-1b-0 Given A8 用户默认 schema 为 forcitis_one 且其下有同名诱饵 ORG_MEMBER（STATE 全为 2）When 读快照 Then 取到 dbo.ORG_MEMBER 的值")
    void baseTablesAreReadByTheirDboName() throws SQLException {
        OrgSource a8 = app().getBean(OrgSource.class);
        SourceOrgSnapshot snapshot;
        try {
            run(MAIN, "SELECT ID, CAST(2 AS smallint) AS STATE, CAST(1 AS smallint) AS IS_DELETED, CAST(0 AS smallint) AS IS_ENABLE "
                            + "INTO forcitis_one.ORG_MEMBER FROM dbo.ORG_MEMBER",
                    "SELECT ID, CAST(N'Account' AS nvarchar(32)) AS TYPE INTO forcitis_one.ORG_UNIT FROM dbo.ORG_UNIT");
            Login reader = LOGINS_BY_DATABASE.get(MAIN);
            try (Connection c = DriverManager.getConnection(url(MAIN) + ";loginTimeout=30", reader.user(), reader.password());
                    Statement s = c.createStatement();
                    ResultSet rs = s.executeQuery("SELECT STATE FROM ORG_MEMBER WHERE ID = " + P1.id())) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("the trap is armed: for the A8 login an unqualified ORG_MEMBER is the decoy").isEqualTo(2);
            }
            snapshot = a8.readSnapshot();
        } finally {
            run(MAIN, "DROP TABLE IF EXISTS forcitis_one.ORG_MEMBER", "DROP TABLE IF EXISTS forcitis_one.ORG_UNIT");
        }

        String printed = snapshot.staff().stream().filter(p -> p.personId().value() == P1.id()).findFirst().orElseThrow().toString();
        assertThat(components(EMPLOYMENT_COMPONENT, printed)).as("dbo.ORG_MEMBER.STATE = 1, not the decoy: %s", printed).containsExactly(SOURCE_EMPLOYED);
        assertThat(components(RECORD_COMPONENT, printed)).as("dbo flags, not the decoy: %s", printed).containsExactly(RECORD_ACTIVE);
    }

    // ------------------------------------------------------------------ 9

    @Test
    @DisplayName("R-1b-0 Given dbo.ORG_MEMBER 缺 STATE When 启动 Then 拒绝启动并报出缺的表与列")
    void missingMemberStateRefusesStartup() throws Exception {
        String database = "a8nostate";
        createA8Database(database, "ALTER TABLE dbo.ORG_MEMBER DROP COLUMN STATE");

        Throwable failure = startAndClose(database);

        assertThat(failure).as("dbo.ORG_MEMBER has no STATE: the application started anyway").isNotNull();
        assertThat(messages(failure)).as("names the table and the column").containsPattern("ORG_MEMBER[^\\n]*\\bSTATE\\b|\\bSTATE\\b[^\\n]*ORG_MEMBER");
    }

    @Test
    @DisplayName("R-1b-0 Given dbo.ORG_UNIT 缺 TYPE When 启动 Then 拒绝启动并报出缺的表与列")
    void missingUnitTypeRefusesStartup() throws Exception {
        String database = "a8notype";
        createA8Database(database, "ALTER TABLE dbo.ORG_UNIT DROP COLUMN TYPE");

        Throwable failure = startAndClose(database);

        assertThat(failure).as("dbo.ORG_UNIT has no TYPE: the application started anyway").isNotNull();
        assertThat(messages(failure)).as("names the table and the column").containsPattern("ORG_UNIT[^\\n]*\\bTYPE\\b|\\bTYPE\\b[^\\n]*ORG_UNIT");
    }

    @Test
    @DisplayName("R-1b-0 Given 基表多出任意列 When 启动 Then 正常（基表做子集检查）")
    void extraBaseTableColumnsAreAccepted() throws Exception {
        String database = "a8extracolumns";
        createA8Database(database, "ALTER TABLE dbo.ORG_MEMBER ADD R1B0_PROBE_EXTRA nvarchar(8) NULL",
                "ALTER TABLE dbo.ORG_UNIT ADD R1B0_PROBE_EXTRA nvarchar(8) NULL");

        Throwable failure = startAndClose(database);

        assertThat(failure).as("a base table with more columns than registered must start; refused with:\n%s", messages(failure)).isNull();
    }

    @Test
    @DisplayName("R-1b-0 Given 视图多一列 When 启动 Then 仍拒绝（经视图读取的路径仍做相等校验）")
    void extraViewColumnStillRefuses() throws Exception {
        String database = "a8viewextra";
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
        String database = "a8migration";
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
    @DisplayName("R-1b-0 Given 同一夹具 When 新人员与部门语句与改动前纯视图语句各执行一次 Then 两个方向 EXCEPT 都为 0 行")
    void newStatementsEqualThePureViewStatements() throws SQLException {
        OrgSource a8 = app().getBean(OrgSource.class);
        run("master", "DBCC FREEPROCCACHE WITH NO_INFOMSGS");
        a8.readSnapshot();
        List<String> sent = cachedStatements(MAIN);

        String members = single(sent, "v_org_member", "person");
        String units = single(sent, "v_org_unit", "org unit");
        assertThat(members).as("the person statement reads the flags from dbo.ORG_MEMBER next to the view:\n%s", members)
                .containsPattern("(?i)\\[?dbo\\]?\\s*\\.\\s*\\[?ORG_MEMBER\\b");
        assertThat(units).as("the org unit statement reads TYPE from dbo.ORG_UNIT next to the view:\n%s", units)
                .containsPattern("(?i)\\[?dbo\\]?\\s*\\.\\s*\\[?ORG_UNIT\\b");

        assertThat(exceptCount(members, MEMBER_VIEW_COLUMNS, PURE_VIEW_MEMBERS)).as("new person statement EXCEPT pure view statement").isZero();
        assertThat(exceptCount(PURE_VIEW_MEMBERS, MEMBER_VIEW_COLUMNS, members)).as("pure view statement EXCEPT new person statement").isZero();
        assertThat(exceptCount(units, UNIT_VIEW_COLUMNS, PURE_VIEW_UNITS)).as("new org unit statement EXCEPT pure view statement").isZero();
        assertThat(exceptCount(PURE_VIEW_UNITS, UNIT_VIEW_COLUMNS, units)).as("pure view statement EXCEPT new org unit statement").isZero();
        assertThat(rowCount(PURE_VIEW_MEMBERS)).as("fixture sanity: the comparison is over real rows").isEqualTo(PEOPLE.stream().filter(Person::inScope).count());
        assertThat(rowCount(PURE_VIEW_UNITS)).isEqualTo(3);
    }

    // ------------------------------------------------------------------ RULING-R1B0-5

    /**
     * A WeCom login of someone missing from the snapshot looks the person up in A8 through {@link OrgDirectory#findEmployeeByWecomUserId}; the
     * login is refused exactly when that answers no employee. Asserted at this public port of workforce: a full HTTP login needs identity data
     * (roles, accounts) that is not this module's (CS-5.5).
     */
    @Test
    @DisplayName("R-1b-0 RULING-R1B0-5 对照：企业微信 userid 命中快照外、标志合法的人，单人查找登记并返回该员工（登录可继续）")
    void loginMissOfALegalSourcePersonRegistersTheEmployee() throws SQLException {
        Person person = LOGIN_CONTROL;
        assertSnapshotHoldsUnitsAndPositions();
        OrgDirectory directory = app().getBean(OrgDirectory.class);
        assertThat(directory.findEmployee(person.employeeId())).as("not in the snapshot yet").isEmpty();
        Optional<EmployeeProfile> found;
        try {
            run(MAIN, personRows(person).toArray(String[]::new));
            found = loginLookup(directory, person);
        } finally {
            run(MAIN, "DELETE dbo.ORG_PRINCIPAL WHERE MEMBER_ID = " + person.id(), "DELETE dbo.ORG_MEMBER WHERE ID = " + person.id());
        }

        assertThat(found).as("the login-miss path itself works").isPresent();
        assertThat(found.get().employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
    }

    @Test
    @DisplayName("R-1b-0 RULING-R1B0-5 Given 企业微信 userid 命中快照外、STATE 非法的人 When 登录查人 Then 不返回员工（登录被拒，失败关闭）、不登记、日志 ALERT，不按来源不可用降级")
    void loginMissOfABrokenSourcePersonIsRefused(CapturedOutput output) throws SQLException {
        Person victim = LOGIN_VICTIM;
        assertSnapshotHoldsUnitsAndPositions();
        OrgDirectory directory = app().getBean(OrgDirectory.class);
        assertThat(directory.findEmployee(victim.employeeId())).as("not in the snapshot: the login falls back to the single-person source lookup").isEmpty();
        int mark = output.getAll().length();
        List<Optional<EmployeeProfile>> answer = new ArrayList<>();
        Throwable failure;
        try {
            run(MAIN, personRows(victim).toArray(String[]::new));
            failure = catchThrowable(() -> answer.add(loginLookup(directory, victim)));
        } finally {
            run(MAIN, "DELETE dbo.ORG_PRINCIPAL WHERE MEMBER_ID = " + victim.id(), "DELETE dbo.ORG_MEMBER WHERE ID = " + victim.id());
        }
        String log = output.getAll().substring(mark);

        assertThat(answer.stream().flatMap(Optional::stream).toList()).as("no employee from a broken row: the login is refused (fail closed)").isEmpty();
        assertThat(directory.findEmployee(victim.employeeId())).as("the person is not registered from a broken row").isEmpty();
        assertThat(log).as("an ALERT, unlike the WARN of an unavailable source").contains("ALERT");
        String told = log + "\n" + (failure == null ? "" : messages(failure));
        assertThat(told).as("no personal data, no SQL Server message").doesNotContain(victim.name()).doesNotContain(victim.login())
                .doesNotContain(Long.toString(victim.id())).doesNotContain("SQLServerException");
    }

    /** Inside a work context, as the login request runs: registering a person publishes an event, which needs an actor. */
    static Optional<EmployeeProfile> loginLookup(OrgDirectory directory, Person person) {
        try (WorkContext.Scope login = WorkContext.enter(UUID.randomUUID(), null, "platform.identity.test")) {
            return directory.findEmployeeByWecomUserId(new WecomUserId(person.wecom()));
        }
    }

    /** A login miss registers a person only into org units and positions the snapshot already holds. */
    static void assertSnapshotHoldsUnitsAndPositions() {
        SyncResult sync = runSync();
        assertThat(sync.status()).as("fixture sanity: %s", sync.note()).isEqualTo(SyncRunStatus.SUCCEEDED);
    }

    // ------------------------------------------------------------------ helpers: sync report

    static SyncResult runSync() {
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.test")) {
            return app().getBean(SyncOrgSnapshot.class).execute(SyncTrigger.MANUAL, false);
        }
    }

    static SyncRunDetailView getRun(SyncResult result) {
        return app().getBean(GetSyncRun.class).execute(result.syncRunId()).orElseThrow();
    }

    static EmployeeProfile employee(Person person) {
        return app().getBean(OrgDirectory.class).findEmployee(person.employeeId()).orElseThrow(
                () -> new AssertionError(person.label() + " is not in the snapshot"));
    }

    static String unitName(long unitId) {
        return app().getBean(OrgDirectory.class).findOrgUnit(new OrgUnitId(unitId)).orElseThrow().name();
    }

    /** The source-state issues of one person: kinds new with R-1b-0, as many times as they occur. */
    static List<String> issueKinds(SyncRunDetailView report, Person person) {
        return report.diffs().stream()
                .filter(d -> person.subject().equals(d.subjectKey()))
                .map(SyncDiffView::issueKind)
                .filter(k -> k != null && !ISSUE_KINDS_BEFORE.contains(k))
                .toList();
    }

    static void assertNoPersonalDataInSourceStateIssues(SyncRunDetailView report) {
        for (SyncDiffView diff : report.diffs()) {
            if (diff.issueKind() == null || ISSUE_KINDS_BEFORE.contains(diff.issueKind())) {
                continue;
            }
            for (Person person : PEOPLE) {
                String detail = String.valueOf(diff.detail());
                assertThat(detail).as("detail of %s carries no A8 personal data", diff.subjectKey()).doesNotContain(person.name());
                if (person.login() != null) {
                    assertThat(detail).as("detail of %s carries no login name", diff.subjectKey()).doesNotContain(person.login());
                }
            }
        }
    }

    static void assertNoThirdNewKind(SyncRunDetailView report) {
        assertThat(report.diffs().stream().map(SyncDiffView::issueKind).filter(k -> k != null && !ISSUE_KINDS_BEFORE.contains(k))
                .collect(Collectors.toSet())).as("only the two source-state kinds are new").isSubsetOf(ISSUE_EMPLOYMENT_DISAGREES, ISSUE_INACTIVE_RECORD_EMPLOYED);
    }

    static List<String> components(Pattern pattern, String printed) {
        List<String> found = new ArrayList<>();
        Matcher m = pattern.matcher(printed);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    // ------------------------------------------------------------------ helpers: statements

    static Map<Long, String> pureViewLoginsById(String database) throws SQLException {
        Map<Long, String> logins = new LinkedHashMap<>();
        try (Connection c = admin(database); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(DECLARE_PURE_VIEW_PREFIX + PURE_VIEW_MEMBERS)) {
            while (rs.next()) {
                logins.put(rs.getLong("ID"), rs.getString("LOGIN_NAME"));
            }
        }
        return logins;
    }

    static List<String> productionCombinationsInFixture() throws SQLException {
        List<String> found = new ArrayList<>();
        Set<Long> production = PRODUCTION_COMBINATIONS.stream().map(Person::id).collect(Collectors.toSet());
        try (Connection c = admin(MAIN); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT DISTINCT M.ID, M.STATE, M.IS_DELETED, M.IS_ENABLE, "
                        + "CASE WHEN EXISTS (SELECT 1 FROM dbo.ORG_PRINCIPAL P WHERE P.MEMBER_ID = M.ID) THEN 'yes' ELSE 'no' END "
                        + "FROM dbo.ORG_MEMBER M")) {
            Set<String> combos = new TreeSet<>();
            while (rs.next()) {
                if (production.contains(rs.getLong(1))) {
                    combos.add(rs.getInt(2) + "|" + rs.getInt(3) + "|" + rs.getInt(4) + "|" + rs.getString(5));
                }
            }
            found.addAll(combos);
        }
        return found;
    }

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
                "--spring.main.banner-mode=off",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--flowable.async-executor-activate=false",
                "--forcitis.platform.approval.deadline.enabled=false",
                "--forcitis.platform.integration.legacy.enabled=false",
                "--forcitis.platform.approval.legacy.url=",
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

    /** Polls in the calling thread: this runs inside the static initializer. */
    private static void waitForAdministratorLogin() {
        Awaitility.await("SQL Server accepts the administrator login")
                .atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500)).pollInSameThread().ignoreExceptions().until(() -> {
                    admin("master").close();
                    return true;
                });
    }

    /** A8-shaped base tables, the DBA view script as is, then the variant, then a baseline read-only login checked against the RDS export. */
    static void createA8Database(String database, String... variant) throws SQLException, IOException {
        run("master", "CREATE DATABASE " + database);
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
        String login = "flags_ro_" + LOGINS.incrementAndGet();
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
