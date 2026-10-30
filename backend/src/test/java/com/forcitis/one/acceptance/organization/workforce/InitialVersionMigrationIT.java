package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.DONE;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #960 (one-design DD on #960, issue comment 6529863330; W2 design sections 2, 3.2, 4 and 6; checklist 14.2 M7): after the initial
 * migration every employee has exactly one current employee version in force today, consistent with the snapshot; a rerun changes
 * nothing; people who already hold versions keep them row for row. The employment phase of migrated employed people is the user's
 * fallback answer (「一律记正式」, fragment 190150), pinned in one case only so that it can follow the source-system evidence if it lands.
 */
class InitialVersionMigrationIT extends InitialVersionMigrationSupport {

    static final TestDatabase postgres = TestPostgres.database(InitialVersionMigrationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#960 数据设计第 3、4 节：快照里在职、没有任何版本的人：一条 MIGRATION 员工版本（今天起开放、收费标记取快照、非停薪留职、入职日未知左截断、无命令无审计 ID），并补一条任职版本（部门、直属领导取快照）")
    void anEmployedSnapshotPersonGetsOneMigratedVersion() throws Exception {
        UUID leader = snapshotPerson(false, null);
        UUID person = snapshotPerson(true, leader);

        Result result = migrate();

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        List<Map<String, Object>> versions = currentVersions(EMPLOYEE_VERSIONS, person);
        assertThat(versions).hasSize(1);
        Map<String, Object> version = versions.get(0);
        assertThat(version.get("employment_status")).isEqualTo(EMPLOYED);
        assertThat(version.get("is_fee_earner")).isEqualTo(true);
        assertThat(version.get("is_on_unpaid_leave")).isEqualTo(false);
        assertThat(version.get("effective_from_date")).as("no assignment version: the day of the run").isEqualTo(TODAY);
        assertThat(version.get("effective_to_date")).isNull();
        assertThat(version.get("change_channel_kind")).isEqualTo("MIGRATION");
        assertThat(version.get("change_command_id")).isNull();
        assertThat(version.get("version_audit_entry_id")).isNull();
        assertThat(version.get("revision_number")).isEqualTo(0);
        assertThat(version.get("employment_start_date")).isNull();
        assertThat(version.get("employment_start_basis_kind")).isEqualTo("UNKNOWN");
        List<Map<String, Object>> assignments = currentVersions(ASSIGNMENT_VERSIONS, person);
        assertThat(assignments).hasSize(1);
        assertThat(fromStored(assignments.get(0).get("org_unit_id"))).isEqualTo(unit);
        assertThat(fromStored(assignments.get(0).get("reports_to_employee_id"))).isEqualTo(leader);
        assertThat(assignments.get(0).get("effective_from_date")).isEqualTo(TODAY);
        assertThat(assignments.get(0).get("effective_to_date")).isNull();
        assertThat(assignments.get(0).get("change_channel_kind")).isEqualTo("MIGRATION");
    }

    @Test
    @DisplayName("#960 用户兜底答复「一律记正式」（fragment 190150；来源系统的转正取证到达后只改本例）：迁入的在职版本雇佣阶段为 REGULAR")
    void migratedEmployedPeopleAreRegular() throws Exception {
        UUID person = snapshotPerson(false, null);

        migrate();

        List<Map<String, Object>> versions = currentVersions(EMPLOYEE_VERSIONS, person);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).get("employment_phase_kind")).isEqualTo(REGULAR);
    }

    @Test
    @DisplayName("#960 数据设计第 3 节（左边界 L）与第 4 节：员工版本从此人最早的未作废任职版本起；同步已写的任职版本一行不动、不补写")
    void theLeftEdgeIsTheEarliestAssignmentVersion() throws Exception {
        UUID person = snapshotPerson(false, null);
        syncAssignmentVersion(person, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 1));
        syncAssignmentVersion(person, LocalDate.of(2026, 6, 1), null);
        List<Map<String, Object>> assignmentsBefore = allVersions(ASSIGNMENT_VERSIONS, person);

        migrate();

        List<Map<String, Object>> versions = currentVersions(EMPLOYEE_VERSIONS, person);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).get("effective_from_date")).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(allVersions(ASSIGNMENT_VERSIONS, person)).as("the sync's assignment versions are kept as they are").isEqualTo(assignmentsBefore);
    }

    @Test
    @DisplayName("#960 数据设计第 3 节（已离职）：离职日 S = separated_at 的业务时区日期；S 晚于 L 时两行：在职 [L, S) 已关闭、离职 [S, 开放)，离职行无阶段、无入职日依据、非收费")
    void aSeparatedSnapshotPersonGetsEmployedThenSeparated() throws Exception {
        UUID person = snapshotPerson(true, null);
        syncAssignmentVersion(person, LocalDate.of(2026, 3, 1), null);
        // 2026-09-14 17:00 UTC is 2026-09-15 01:00 in Asia/Shanghai: the business day is the 15th.
        snapshotSeparated(person, Instant.parse("2026-09-14T17:00:00Z"));

        migrate();

        List<Map<String, Object>> versions = currentVersions(EMPLOYEE_VERSIONS, person);
        assertThat(versions).hasSize(2);
        assertContiguous(versions);
        Map<String, Object> employed = versions.get(0);
        Map<String, Object> separated = versions.get(1);
        assertThat(employed.get("employment_status")).isEqualTo(EMPLOYED);
        assertThat(employed.get("effective_from_date")).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(employed.get("effective_to_date")).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(employed.get("closed_at")).isNotNull();
        assertThat(employed.get("is_fee_earner")).isEqualTo(true);
        assertThat(separated.get("employment_status")).isEqualTo(SEPARATED);
        assertThat(separated.get("effective_from_date")).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(separated.get("employment_phase_kind")).isNull();
        assertThat(separated.get("employment_start_basis_kind")).isNull();
        assertThat(separated.get("is_fee_earner")).isEqualTo(false);
        assertThat(separated.get("is_on_unpaid_leave")).isEqualTo(false);
        assertThat(versions).allSatisfy(row -> assertThat(row.get("change_channel_kind")).isEqualTo("MIGRATION"));
    }

    @Test
    @DisplayName("#960 数据设计第 3 节（已离职，无离职时刻或离职不晚于 L）：只写一条离职版本 [L, 开放)")
    void aSeparatedPersonWithoutALaterSeparationDayGetsOneSeparatedVersion() throws Exception {
        UUID noInstant = snapshotPerson(false, null);
        syncAssignmentVersion(noInstant, LocalDate.of(2026, 3, 1), null);
        snapshotSeparated(noInstant, null);
        UUID before = snapshotPerson(false, null);
        syncAssignmentVersion(before, LocalDate.of(2026, 3, 1), null);
        snapshotSeparated(before, Instant.parse("2026-02-10T02:00:00Z"));

        migrate();

        for (UUID person : List.of(noInstant, before)) {
            List<Map<String, Object>> versions = currentVersions(EMPLOYEE_VERSIONS, person);
            assertThat(versions).as(person.toString()).hasSize(1);
            assertThat(versions.get(0).get("employment_status")).isEqualTo(SEPARATED);
            assertThat(versions.get(0).get("effective_from_date")).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(versions.get(0).get("effective_to_date")).isNull();
        }
    }

    @Test
    @DisplayName("#960 完成标准（已有命令版本者保留）：已有版本的人（迁入种子、人事命令写的）两张版本表逐行不变")
    void peopleWhoHoldVersionsKeepThem() throws Exception {
        UUID seeded = employed(PROBATION);
        UUID commanded = employed(REGULAR);
        transfer(commanded, TODAY.plusDays(3), newUnit());
        List<List<Map<String, Object>>> before = List.of(allVersions(EMPLOYEE_VERSIONS, seeded), allVersions(ASSIGNMENT_VERSIONS, seeded),
                allVersions(EMPLOYEE_VERSIONS, commanded), allVersions(ASSIGNMENT_VERSIONS, commanded));

        migrate();

        assertThat(List.of(allVersions(EMPLOYEE_VERSIONS, seeded), allVersions(ASSIGNMENT_VERSIONS, seeded),
                allVersions(EMPLOYEE_VERSIONS, commanded), allVersions(ASSIGNMENT_VERSIONS, commanded))).isEqualTo(before);
    }

    @Test
    @DisplayName("#960 完成标准：迁入后每名员工今天恰有一条当前员工版本；被迁入的人在职状态、收费标记与快照一致")
    void afterTheMigrationEveryEmployeeHasExactlyOneCurrentVersion() throws Exception {
        UUID employed = snapshotPerson(true, null);
        UUID separated = snapshotPerson(false, null);
        snapshotSeparated(separated, Instant.parse("2026-10-01T02:00:00Z"));

        migrate();

        List<Map<String, Object>> perEmployee = rows("""
                select e.id, e.employment_status as snapshot_status, e.is_fee_earner as snapshot_fee_earner,
                       (select count(*) from employee_versions v where v.employee_id = e.id and v.superseded_at is null
                           and v.effective_from_date <= ? and (v.effective_to_date is null or v.effective_to_date > ?)) as current_versions
                  from employees e""", TODAY, TODAY);
        assertThat(perEmployee).isNotEmpty().allSatisfy(row -> assertThat(row.get("current_versions")).as(String.valueOf(row.get("id"))).isEqualTo(1L));
        Map<String, Object> employedNow = inForceOn(currentVersions(EMPLOYEE_VERSIONS, employed), TODAY);
        assertThat(employedNow.get("employment_status")).isEqualTo(EMPLOYED);
        assertThat(employedNow.get("is_fee_earner")).isEqualTo(true);
        assertThat(inForceOn(currentVersions(EMPLOYEE_VERSIONS, separated), TODAY).get("employment_status")).isEqualTo(SEPARATED);
    }

    @Test
    @DisplayName("#960 完成标准（重跑无变化）与 14.2 M7：第二次运行成功、两张版本表逐行不变；两次运行都不发领域事件；每次运行写一条审计，审计不含被迁入者的 ID 与姓名")
    void aRerunChangesNothingAndNothingIsPublished() throws Exception {
        UUID person = snapshotPerson(true, null);
        // Operator and witness of both runs are seeded first: seeding attributes them to a legal entity, which publishes events of its own.
        UUID firstOperator = employed(REGULAR);
        UUID firstWitness = employed(REGULAR);
        UUID secondOperator = employed(REGULAR);
        UUID secondWitness = employed(REGULAR);
        long events = publishedEvents();
        int audited = auditCount(AUDIT_MIGRATED);

        Result first = migrate(firstOperator, firstWitness);
        List<Map<String, Object>> afterFirst = everyVersionRow();
        Result second = migrate(secondOperator, secondWitness);

        assertThat(first.exitCode()).as(first.text()).isEqualTo(DONE);
        assertThat(second.exitCode()).as(second.text()).isEqualTo(DONE);
        assertThat(everyVersionRow()).as("the rerun changes no row").isEqualTo(afterFirst);
        assertThat(publishedEvents()).isEqualTo(events);
        assertThat(auditCount(AUDIT_MIGRATED)).isEqualTo(audited + 2);
        String name = (String) rows("select display_name from employees where id = ?", stored(person)).get(0).get("display_name");
        assertThat(auditQuery(null, null, AUDIT_MIGRATED)).isNotEmpty().allSatisfy(entry -> assertThat(entry.values())
                .noneMatch(value -> value != null && (value.toString().contains(text(person)) || value.toString().contains(name))));
    }
}
