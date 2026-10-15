package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P3b, ADR-0033 section 2 item 3 and guard 7 (card section 4 items 2 and 3): the sync matches a source object by its source id through
 * the source references and creates a reference and an object only for a source id it has not seen. A person whose name, employee code
 * and login name all change stays the same own id; the employee code is no key, so two people with the same code each get their entry.
 * The evidence is the number of references and the number of allocation statements, read black-box.
 */
@Testcontainers
class SourceIdMatchingIT extends OrgSourceWorldSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

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
        member(CHANGING, "Before", "E9101", UNIT, UNIT_PATH, null, null, "wx-world-9101", "world-9101");
        syncOnce();
        UUID person = ownIdOf(EMPLOYEE, CHANGING);
        assertThat(snapshotValue("employees", person, "display_name")).isEqualTo("Before");
        int employeeReferences = references(EMPLOYEE);

        renameMember(CHANGING, "After", "E9101-NEW", "world-9101-new");
        countAllocations();
        syncOnce();

        assertThat(ownIdsOf(EMPLOYEE, CHANGING)).as("still the one reference of the same source id").containsExactly(person);
        assertThat(snapshotValue("employees", person, "display_name")).as("the entry of the same own id took the new name").isEqualTo("After");
        assertThat(snapshotValue("employees", person, "employee_code")).isEqualTo("E9101-NEW");
        assertThat(snapshotValue("employees", person, "login_name")).isEqualTo("world-9101-new");
        assertThat(references(EMPLOYEE)).as("no reference is made for a person already seen").isEqualTo(employeeReferences);
        assertThat(allocationStatements()).as("no allocation for objects that have a reference").isZero();

        member(NEWCOMER, "Newcomer", "E9104", UNIT, UNIT_PATH, null, null, "wx-world-9104", "world-9104");
        countAllocations();
        syncOnce();

        UUID newcomer = ownIdOf(EMPLOYEE, NEWCOMER);
        assertThat(newcomer).isNotEqualTo(person);
        assertThat(inSnapshot("employees", newcomer)).isTrue();
        assertThat(references(EMPLOYEE)).as("one reference more, for the one new source id").isEqualTo(employeeReferences + 1);
        assertThat(allocationStatements()).as("one allocation, for the one new source object").isEqualTo(1);
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 7 / 第 6 项第 1 条：员工编号不作键——两人同编号都正常建档，各得一个自有 ID")
    void twoPeopleWithTheSameCodeBothGetAnEntry() throws Exception {
        unit(UNIT + 50, "C9150", "Same code unit", ROOT_PATH + "0022");
        member(SAME_CODE_FIRST, "First", SHARED_CODE, UNIT + 50, ROOT_PATH + "0022", null, null, "wx-world-9102", "world-9102");
        member(SAME_CODE_SECOND, "Second", SHARED_CODE, UNIT + 50, ROOT_PATH + "0022", null, null, "wx-world-9103", "world-9103");

        syncOnce();

        UUID first = ownIdOf(EMPLOYEE, SAME_CODE_FIRST);
        UUID second = ownIdOf(EMPLOYEE, SAME_CODE_SECOND);
        assertThat(second).isNotEqualTo(first);
        assertThat(snapshotValue("employees", first, "employee_code")).isEqualTo(SHARED_CODE);
        assertThat(snapshotValue("employees", second, "employee_code")).isEqualTo(SHARED_CODE);
        assertThat(snapshotValue("employees", first, "display_name")).isEqualTo("First");
        assertThat(snapshotValue("employees", second, "display_name")).isEqualTo("Second");
    }
}
