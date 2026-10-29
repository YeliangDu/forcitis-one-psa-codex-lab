package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P3b, ADR-0033 section 2 item 3 and guard 7 (card section 4 items 2 and 3): the sync matches a source object by its source id through
 * the source references and creates a reference and an object only for a source id it has not seen. A person whose name, employee code
 * and login name all change stays the same own id; the employee code is unique and fixed once issued (ADR-0078), so a second person with a code already issued is isolated and reported.
 * The evidence is the number of references and the number of allocation statements, read black-box.
 */
class SourceIdMatchingIT extends OrgSourceWorldSupport {

    static final TestDatabase postgres = TestPostgres.database(SourceIdMatchingIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String EMPLOYEE = "EMPLOYEE";
    static final String UNIT_PATH = ROOT_PATH + "0021";
    static final long UNIT = 9100;
    static final long CHANGING = 9101;
    static final long SAME_CODE_FIRST = 9102;
    static final long SAME_CODE_SECOND = 9103;
    static final long NEWCOMER = 9104;
    static final String SHARED_CODE = "E-SHARED";

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 2 项第 3 条：同步按来源 ID 匹配，找不到才新建——第二次同步中姓名、员工编号、登录名都变了的人仍是同一自有 ID；只有新的来源 ID 才新建对照与对象；已有对照的对象不再调分配")
    void theSyncMatchesBySourceIdAndCreatesOnlyForNewOnes() throws Exception {
        unit(UNIT, "C9100", "Matching unit", UNIT_PATH);
        member(CHANGING, "Before", "E9101", UNIT, UNIT_PATH, BASE_POST, null, "wx-world-9101", "world-9101");
        String firstRun = syncOnce().syncRunId().value().toString();
        UUID person = ownIdOf(EMPLOYEE, CHANGING);
        assertThat(snapshotValue("employees", person, "display_name")).isEqualTo("Before");
        int employeeReferences = references(EMPLOYEE);

        renameMember(CHANGING, "After", "E9101-NEW", "world-9101-new");
        countAllocations();
        String renameRun = syncOnce().syncRunId().value().toString();

        assertThat(ownIdsOf(EMPLOYEE, CHANGING)).as("still the one reference of the same source id").containsExactly(person);
        assertThat(snapshotValue("employees", person, "display_name")).as("the entry of the same own id took the new name").isEqualTo("After");
        assertThat(snapshotValue("employees", person, "employee_code")).as("the issued code is kept, the changed source code is reported").isEqualTo("E9101");
        assertThat(snapshotValue("employees", person, "login_name")).isEqualTo("world-9101-new");
        assertThat(issuesOfRun(renameRun, "EMPLOYEE_CODE_CHANGE_IGNORED")).as("the ignored code change is reported once").isEqualTo(1);
        assertThat(runCount(renameRun, "issue_count") - runCount(firstRun, "issue_count")).as("the exception counts as one issue (RULING-COORD-20261023-08)").isEqualTo(1);
        assertThat(runCount(renameRun, "isolated_row_count")).as("the person is kept, not isolated").isEqualTo(runCount(firstRun, "isolated_row_count"));
        String detail = issueDetail(renameRun, "EMPLOYEE_CODE_CHANGE_IGNORED", "employee:" + person);
        assertThat(detail).as("the exception names no code value (ADR-0078 decision 8)").isNotBlank().doesNotContain("E9101").doesNotContain("E9101-NEW");
        assertThat(references(EMPLOYEE)).as("no reference is made for a person already seen").isEqualTo(employeeReferences);
        assertThat(allocationStatements()).as("no allocation for objects that have a reference").isZero();

        member(NEWCOMER, "Newcomer", "E9104", UNIT, UNIT_PATH, BASE_POST, null, "wx-world-9104", "world-9104");
        countAllocations();
        syncOnce();

        UUID newcomer = ownIdOf(EMPLOYEE, NEWCOMER);
        assertThat(newcomer).isNotEqualTo(person);
        assertThat(inSnapshot("employees", newcomer)).isTrue();
        assertThat(references(EMPLOYEE)).as("one reference more, for the one new source id").isEqualTo(employeeReferences + 1);
        assertThat(allocationStatements()).as("one allocation, for the one new source object").isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-0078：员工编号全局唯一——两人同编号时来源 ID 小的建档，另一人被隔离并写 DUPLICATE_EMPLOYEE_CODE 例外，重跑结果相同")
    void theSecondPersonWithTheSameCodeIsIsolated() throws Exception {
        unit(UNIT + 50, "C9150", "Same code unit", ROOT_PATH + "0022");
        member(SAME_CODE_FIRST, "First", SHARED_CODE, UNIT + 50, ROOT_PATH + "0022", BASE_POST, null, "wx-world-9102", "world-9102");
        member(SAME_CODE_SECOND, "Second", SHARED_CODE, UNIT + 50, ROOT_PATH + "0022", BASE_POST, null, "wx-world-9103", "world-9103");

        String run = syncOnce().syncRunId().value().toString();

        UUID first = ownIdOf(EMPLOYEE, SAME_CODE_FIRST);
        assertThat(snapshotValue("employees", first, "employee_code")).isEqualTo(SHARED_CODE);
        assertThat(snapshotValue("employees", first, "display_name")).isEqualTo("First");
        assertThat(employeesWithCode(SHARED_CODE)).as("only the first person is built").isEqualTo(1);
        assertThat(issuesOfRun(run, "DUPLICATE_EMPLOYEE_CODE")).as("the second person is reported once").isEqualTo(1);

        String rerun = syncOnce().syncRunId().value().toString();

        assertThat(snapshotValue("employees", first, "display_name")).as("the first person is not affected").isEqualTo("First");
        assertThat(employeesWithCode(SHARED_CODE)).isEqualTo(1);
        assertThat(issuesOfRun(rerun, "DUPLICATE_EMPLOYEE_CODE")).as("the same result on a rerun").isEqualTo(1);
    }

    private int runCount(String run, String column) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement select = workforce.prepareStatement(
                "select coalesce(" + column + ", 0) from sync_runs where id::text = ?")) {
            select.setString(1, run);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).as("sync run " + run).isTrue();
                return rs.getInt(1);
            }
        }
    }

    private int employeesWithCode(String code) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement select = workforce.prepareStatement(
                "select count(*) from employees where employee_code = ?")) {
            select.setString(1, code);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int issuesOfRun(String run, String issueKind) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement select = workforce.prepareStatement(
                "select count(*) from sync_diffs where sync_run_id::text = ? and issue_kind = ?")) {
            select.setString(1, run);
            select.setString(2, issueKind);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String issueDetail(String run, String issueKind, String subjectKey) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement select = workforce.prepareStatement(
                "select detail from sync_diffs where sync_run_id::text = ? and issue_kind = ? and subject_key = ?")) {
            select.setString(1, run);
            select.setString(2, issueKind);
            select.setString(3, subjectKey);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
