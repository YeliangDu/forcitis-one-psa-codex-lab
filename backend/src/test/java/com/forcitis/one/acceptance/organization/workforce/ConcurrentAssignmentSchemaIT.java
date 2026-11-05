package com.forcitis.one.acceptance.organization.workforce;

import com.forcitis.one.acceptance.EmploymentRelationshipSeeds;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #711 acceptance part, database lines (DD revision 2 on #711 sections 4.1 to 4.3 and 5, done-when 1 to 8 and 14 of section 7 and the
 * amendment of 2026-11-02): one PRIMARY root per employee whose versions never overlap (INV-62, user 2026-10-28 「主任职不重叠，兼职不限」),
 * any number of CONCURRENT roots, every version and command tied to its employee's root and kind by composite keys, no reporting line on a
 * concurrent post, open commands counted per person-or-primary and per concurrent post, one generated transfer per assignment per merge,
 * and the same unit and position allowed twice (user answer 3a). Rows are written directly, as a writer that bypassed the application
 * would, so only the database is under test. Constraint names are the DD's; command CHECKs the DD does not name are asserted by SQL state.
 */
class ConcurrentAssignmentSchemaIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(ConcurrentAssignmentSchemaIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";
    private static final String EXCLUSION_VIOLATION = "23P01";

    private OffsetDateTime at;
    private UUID person;
    private UUID primary;

    @FunctionalInterface
    private interface Statement {
        void run() throws Exception;
    }

    @BeforeEach
    void aPersonWithAPrimaryPost() throws Exception {
        at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        person = employed(REGULAR);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            assertThat(AssignmentRoots.exist(workforce)).as("#711 DD 4.1: the table " + AssignmentRoots.TABLE + " exists").isTrue();
            primary = AssignmentRoots.primaryOf(workforce, stored(person));
        }
    }

    private UUID root(UUID employee, String kind) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            return AssignmentRoots.create(workforce, stored(employee), kind);
        }
    }

    /** A MIGRATION version of the root from the day (open), in the unit, with the position and reports-to; revision as given. */
    private UUID version(UUID employee, UUID root, String kind, LocalDate from, UUID orgUnit, UUID position, UUID reportsTo, int revision) throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into assignment_versions (employment_relationship_id, assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date, revision_number,
                      org_unit_id, position_id, reports_to_employee_id, change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""", EmploymentRelationshipSeeds.relationshipOf(workforce, stored(employee)), id, stored(employee), root, kind, from, revision, stored(orgUnit), position, stored(reportsTo), at, at);
        }
        return id;
    }

    private UUID concurrentVersion(UUID root, LocalDate from, UUID orgUnit, UUID position) throws SQLException {
        return version(person, root, AssignmentRoots.CONCURRENT, from, orgUnit, position, null, 0);
    }

    /** A personnel command row; the kind's target columns as given (null leaves a column empty). */
    private void command(UUID employee, String kind, LocalDate day, UUID targetUnit, UUID targetPosition, UUID reportsTo, Boolean feeEarner,
            UUID targetRoot, String targetKind, UUID orgCommand) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into personnel_commands (personnel_command_id, employee_id, employment_relationship_id, command_kind, effective_date, status, target_org_unit_id,
                      target_position_id, target_reports_to_employee_id, is_target_fee_earner, target_assignment_id, target_assignment_kind,
                      org_structure_command_id, created_at, created_by, updated_at)
                    values (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(employee), EmploymentRelationshipSeeds.relationshipOf(workforce, stored(employee)), kind, day, stored(targetUnit), targetPosition, stored(reportsTo), feeEarner, targetRoot, targetKind,
                    orgCommand, at, hr.userId(), at);
        }
    }

    private static void refusedBy(Statement statement, String sqlState, String constraint) {
        SQLException refused = catchThrowableOfType(SQLException.class, statement::run);
        assertThat((Throwable) refused).as("refused by " + (constraint == null ? "a CHECK" : constraint)).isNotNull();
        assertThat(refused.getSQLState()).as(refused.getMessage()).isEqualTo(sqlState);
        if (constraint != null) {
            assertThat(refused.getMessage()).contains("\"" + constraint + "\"");
        }
    }

    // ---- done-when 1, 2, 14 ----

    @Test
    @DisplayName("#711 完成标准 1（INV-62，用户 2026-10-28「主任职不重叠，兼职不限」）：同一员工两条互相重叠、也与主任职重叠的兼职版本都被接受；主任职根上再追加一条重叠版本被 ex_assignment_versions_overlap 拒绝；第二个主任职根被 uk_employee_assignments_primary 拒绝")
    void concurrentPostsOverlapAndThePrimaryDoesNot() throws Exception {
        UUID first = root(person, AssignmentRoots.CONCURRENT);
        UUID second = root(person, AssignmentRoots.CONCURRENT);

        concurrentVersion(first, TODAY.minusDays(20), newUnit(), newPosition());
        concurrentVersion(second, TODAY.minusDays(10), newUnit(), newPosition());

        refusedBy(() -> version(person, primary, AssignmentRoots.PRIMARY, TODAY.minusDays(5), newUnit(), newPosition(), null, 0), EXCLUSION_VIOLATION,
                "ex_assignment_versions_overlap");
        refusedBy(() -> root(person, AssignmentRoots.PRIMARY), UNIQUE_VIOLATION, "uk_employee_assignments_primary");
        assertThat(rows("select count(*) as n from assignment_versions where employee_id = ? and effective_to_date is null", stored(person)).get(0).get("n"))
                .as("the primary and two concurrent posts are open together").isEqualTo(3L);
    }

    @Test
    @DisplayName("#711 完成标准 2（M7 a：修订序号仍按员工与日期编号）：两条同一天开始的任职取修订序号 0 与 1，都被接受；同一天再用序号 0 被 uk_assignment_versions_revision 拒绝")
    void twoPostsStartingTheSameDayTakeRevisionsZeroAndOne() throws Exception {
        UUID other = newEmployeeId();
        employee(other, unit, null);
        UUID otherPrimary = root(other, AssignmentRoots.PRIMARY);
        UUID otherConcurrent = root(other, AssignmentRoots.CONCURRENT);
        LocalDate day = TODAY.minusDays(3);

        version(other, otherPrimary, AssignmentRoots.PRIMARY, day, unit, newPosition(), null, 0);
        version(other, otherConcurrent, AssignmentRoots.CONCURRENT, day, newUnit(), newPosition(), null, 1);

        UUID third = root(other, AssignmentRoots.CONCURRENT);
        refusedBy(() -> version(other, third, AssignmentRoots.CONCURRENT, day, newUnit(), newPosition(), null, 0), UNIQUE_VIOLATION, "uk_assignment_versions_revision");
    }

    @Test
    @DisplayName("#711 完成标准 14（用户答复 3a：同一人可同时两次任同一单元同一岗位）：主任职与兼职的单元、岗位相同且时间重叠，两条都被接受")
    void theSameUnitAndPositionTwiceAreAccepted() throws Exception {
        UUID position = positionHeldNow();
        UUID concurrent = root(person, AssignmentRoots.CONCURRENT);

        concurrentVersion(concurrent, TODAY.minusDays(1), unit, position);

        assertThat(rows("select count(*) as n from assignment_versions where employee_id = ? and org_unit_id = ? and position_id = ? and effective_to_date is null",
                stored(person), stored(unit), position).get(0).get("n")).isEqualTo(2L);
    }

    private UUID positionHeldNow() throws SQLException {
        return (UUID) rows("select position_id from assignment_versions where assignment_id = ? and effective_to_date is null", primary).get(0).get("position_id");
    }

    // ---- done-when 3, 4 ----

    @Test
    @DisplayName("#711 完成标准 3（复合外键）：版本挂别人的任职根、或类别与根不符（兼职根写成主任职），被 fk_assignment_versions_assignment 拒绝；人事命令指向别人的任职根被 fk_personnel_commands_target_assignment 拒绝")
    void versionsAndCommandsCannotNameAnotherEmployeesPost() throws Exception {
        UUID stranger = employed(REGULAR);
        UUID strangersConcurrent = root(stranger, AssignmentRoots.CONCURRENT);
        UUID mine = root(person, AssignmentRoots.CONCURRENT);

        refusedBy(() -> version(person, strangersConcurrent, AssignmentRoots.CONCURRENT, TODAY.minusDays(2), newUnit(), newPosition(), null, 0), FOREIGN_KEY_VIOLATION,
                "fk_assignment_versions_assignment");
        refusedBy(() -> version(person, mine, AssignmentRoots.PRIMARY, TODAY.minusDays(2), newUnit(), newPosition(), null, 0), FOREIGN_KEY_VIOLATION,
                "fk_assignment_versions_assignment");
        refusedBy(() -> command(person, "TRANSFER", TODAY.plusDays(3), newUnit(), null, null, null, strangersConcurrent, AssignmentRoots.CONCURRENT, null),
                FOREIGN_KEY_VIOLATION, "fk_personnel_commands_target_assignment");
        refusedBy(() -> command(person, "TRANSFER", TODAY.plusDays(3), newUnit(), null, null, null, mine, AssignmentRoots.PRIMARY, null),
                FOREIGN_KEY_VIOLATION, "fk_personnel_commands_target_assignment");
        command(person, "TRANSFER", TODAY.plusDays(3), newUnit(), null, null, null, mine, AssignmentRoots.CONCURRENT, null);
    }

    @Test
    @DisplayName("#711 完成标准 4（M6：兼职没有汇报线）：兼职版本带直属领导被 ck_assignment_versions_concurrent_reports_to 拒绝（主任职带直属领导照常）；新增兼职带直属领导、兼职调岗带直属领导或计费标记，都被各自的 CHECK 拒绝")
    void aConcurrentPostHasNoReportingLine() throws Exception {
        UUID leader = employed(REGULAR);
        UUID concurrent = root(person, AssignmentRoots.CONCURRENT);

        refusedBy(() -> version(person, concurrent, AssignmentRoots.CONCURRENT, TODAY.minusDays(2), newUnit(), newPosition(), leader, 0), CHECK_VIOLATION,
                "ck_assignment_versions_concurrent_reports_to");
        UUID other = newEmployeeId();
        employee(other, unit, null);
        UUID otherPrimary = root(other, AssignmentRoots.PRIMARY);
        version(other, otherPrimary, AssignmentRoots.PRIMARY, TODAY.minusDays(2), unit, newPosition(), leader, 0);

        refusedBy(() -> command(person, "START_CONCURRENT", TODAY.plusDays(2), newUnit(), newPosition(), leader, null, null, null, null), CHECK_VIOLATION, null);
        refusedBy(() -> command(person, "TRANSFER", TODAY.plusDays(2), newUnit(), null, leader, null, concurrent, AssignmentRoots.CONCURRENT, null), CHECK_VIOLATION, null);
        refusedBy(() -> command(person, "TRANSFER", TODAY.plusDays(2), newUnit(), null, null, true, concurrent, AssignmentRoots.CONCURRENT, null), CHECK_VIOLATION, null);
        command(person, "START_CONCURRENT", TODAY.plusDays(2), newUnit(), newPosition(), null, null, null, null, null);
    }

    // ---- done-when 5, 6 ----

    @Test
    @DisplayName("#711 完成标准 5：调岗不带目标任职、结束兼职指向主任职根或带别的目标列、新增兼职带目标任职、离职与转正带目标任职，都被 CHECK 拒绝（DD 4.3）；结束兼职指向兼职根被接受")
    void commandsNameTheRightKindOfPost() throws Exception {
        UUID concurrent = root(person, AssignmentRoots.CONCURRENT);

        refusedBy(() -> command(person, "TRANSFER", TODAY.plusDays(4), newUnit(), null, null, null, null, null, null), CHECK_VIOLATION, null);
        refusedBy(() -> command(person, "END_CONCURRENT", TODAY.plusDays(4), null, null, null, null, primary, AssignmentRoots.PRIMARY, null), CHECK_VIOLATION, null);
        refusedBy(() -> command(person, "START_CONCURRENT", TODAY.plusDays(4), newUnit(), newPosition(), null, null, concurrent, AssignmentRoots.CONCURRENT, null),
                CHECK_VIOLATION, null);
        refusedBy(() -> command(person, "END_CONCURRENT", TODAY.plusDays(4), newUnit(), null, null, null, concurrent, AssignmentRoots.CONCURRENT, null),
                CHECK_VIOLATION, null);
        refusedBy(() -> command(person, "SEPARATE", TODAY.plusDays(4), null, null, null, null, primary, AssignmentRoots.PRIMARY, null), CHECK_VIOLATION, null);
        refusedBy(() -> command(person, "REGULARIZE", TODAY.plusDays(4), null, null, null, null, primary, AssignmentRoots.PRIMARY, null), CHECK_VIOLATION, null);
        command(person, "END_CONCURRENT", TODAY.plusDays(4), null, null, null, null, concurrent, AssignmentRoots.CONCURRENT, null);
    }

    @Test
    @DisplayName("#711 完成标准 6（S2：更正留在自己的任职上）：更正行的被更正版本属于另一条任职时，被复合外键 fk_assignment_versions_corrected_from 拒绝；指向同一任职的版本被接受")
    void aCorrectionStaysOnItsAssignment() throws Exception {
        UUID concurrent = root(person, AssignmentRoots.CONCURRENT);
        UUID concurrentVersion = concurrentVersion(concurrent, TODAY.minusDays(9), newUnit(), newPosition());
        UUID primaryVersion = (UUID) rows("select assignment_version_id from assignment_versions where assignment_id = ?", primary).get(0).get("assignment_version_id");

        refusedBy(() -> correctionOf(concurrent, primaryVersion), FOREIGN_KEY_VIOLATION, "fk_assignment_versions_corrected_from");
        correctionOf(concurrent, concurrentVersion);
    }

    /** A closed row of the concurrent root, before every other version, that names the corrected version (only the composite FK is under test). */
    private void correctionOf(UUID concurrent, UUID corrected) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into assignment_versions (employment_relationship_id, assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date, effective_to_date,
                      revision_number, org_unit_id, change_channel_kind, corrected_from_version_id, closed_at, created_at, updated_at)
                    values (?, ?, ?, ?, 'CONCURRENT', ?, ?, 5, ?, 'MIGRATION', ?, ?, ?, ?)""", EmploymentRelationshipSeeds.relationshipOf(workforce, stored(person)), Uuids.timeOrdered(NOW), stored(person), concurrent, TODAY.minusDays(40), TODAY.minusDays(39), stored(newUnit()), corrected, at, at, at);
        }
    }

    // ---- done-when 7, 8 ----

    @Test
    @DisplayName("#711 完成标准 7（M2：按人或主任职、按兼职分别一天一条未生效命令）：同一天主任职调岗加新增兼职、结束兼职加主任职调岗、再加另一个兼职的调岗都被接受（按兼职各算，rv1045x M1）；主任职同一天两条被 uk_personnel_commands_open_per_day 拒绝；同一兼职同一天两条被 uk_personnel_commands_open_per_assignment_day 拒绝")
    void oneOpenCommandPerPersonOrPrimaryAndPerConcurrentPostPerDay() throws Exception {
        UUID concurrent = root(person, AssignmentRoots.CONCURRENT);
        concurrentVersion(concurrent, TODAY.minusDays(9), newUnit(), newPosition());
        LocalDate first = TODAY.plusDays(5);
        LocalDate second = TODAY.plusDays(6);

        command(person, "TRANSFER", first, newUnit(), null, null, null, primary, AssignmentRoots.PRIMARY, null);
        command(person, "START_CONCURRENT", first, newUnit(), newPosition(), null, null, null, null, null);
        command(person, "END_CONCURRENT", second, null, null, null, null, concurrent, AssignmentRoots.CONCURRENT, null);
        command(person, "TRANSFER", second, newUnit(), null, null, null, primary, AssignmentRoots.PRIMARY, null);
        UUID secondPost = root(person, AssignmentRoots.CONCURRENT);
        concurrentVersion(secondPost, TODAY.minusDays(8), newUnit(), newPosition());
        command(person, "TRANSFER", second, newUnit(), null, null, null, secondPost, AssignmentRoots.CONCURRENT, null);

        refusedBy(() -> command(person, "TRANSFER", first, newUnit(), null, null, null, primary, AssignmentRoots.PRIMARY, null), UNIQUE_VIOLATION,
                "uk_personnel_commands_open_per_day");
        refusedBy(() -> command(person, "TRANSFER", second, newUnit(), null, null, null, concurrent, AssignmentRoots.CONCURRENT, null), UNIQUE_VIOLATION,
                "uk_personnel_commands_open_per_assignment_day");
    }

    @Test
    @DisplayName("#711 完成标准 8（M1）：同一合并给同一员工三条任职（主任职与两个兼职）各生成一条调岗被接受（每条任职一条，rv1045x M1）；同一任职两条被 uk_personnel_commands_merge_assignment 拒绝")
    void oneGeneratedTransferPerAssignmentPerMerge() throws Exception {
        UUID concurrent = root(person, AssignmentRoots.CONCURRENT);
        concurrentVersion(concurrent, TODAY.minusDays(9), unit, newPosition());
        UUID merge = mergeInProgress();
        LocalDate day = TODAY;

        command(person, "TRANSFER", day, newUnit(), null, null, null, primary, AssignmentRoots.PRIMARY, merge);
        command(person, "TRANSFER", day, newUnit(), null, null, null, concurrent, AssignmentRoots.CONCURRENT, merge);
        UUID secondPost = root(person, AssignmentRoots.CONCURRENT);
        concurrentVersion(secondPost, TODAY.minusDays(8), unit, newPosition());
        command(person, "TRANSFER", day, newUnit(), null, null, null, secondPost, AssignmentRoots.CONCURRENT, merge);

        refusedBy(() -> command(person, "TRANSFER", day.plusDays(1), newUnit(), null, null, null, concurrent, AssignmentRoots.CONCURRENT, merge), UNIQUE_VIOLATION,
                "uk_personnel_commands_merge_assignment");
    }

    /** A MERGE in progress that needed no countersign (no rows at all), so that the #908 merge-transfer guard, once it exists, admits its transfers. */
    private UUID mergeInProgress() throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        UUID merged = newOrgUnitId();
        orgUnit(merged, null, null);
        UUID receiving = newOrgUnitId();
        orgUnit(receiving, null, null);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,
                      merge_into_org_unit_id, created_at, created_by, updated_at) values (?, 'MERGE', 'ORG_UNIT', ?, ?, 'PENDING', ?, ?, ?, ?)""",
                    id, stored(merged), TODAY, stored(receiving), at, hr.userId(), at);
            update(workforce, "update org_structure_commands set status = 'IN_PROGRESS', affected_employee_count = 2, transferred_employee_count = 0, updated_at = ?"
                    + " where org_structure_command_id = ?", at, id);
        }
        return id;
    }
}
