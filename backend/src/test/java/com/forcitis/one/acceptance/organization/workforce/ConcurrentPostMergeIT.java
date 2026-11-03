package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.EVENT_MERGED;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.IN_PROGRESS;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.REVOKED;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.COMMAND_KIND;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE_DATE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EMPLOYEE_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EVENT_TRANSFERRED;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TARGET_ORG_UNIT_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TARGET_POSITION_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1040 acceptance (one-design design on #1040 comment 6591756163, built on #711's signed shape; DD amendment E moved here from #907/#910;
 * #910's QF1 ruling 6589166321): a merge moves every current assignment in the merged unit on the merge date, primary and concurrent, with
 * one generated TRANSFER per assignment. A concurrent move is always exempt (no basis row, no countersign), EFFECTIVE in the execution
 * transaction, publishes nothing. Progress counts employees across all their assignments in the unit; the merge is EFFECTIVE only when no
 * assignment is left there; while IN_PROGRESS nothing may move in, START_CONCURRENT included. The same post held twice keeps both. The
 * preview stays one item per employee and adds primaryInUnit and concurrentAssignmentCount. Concurrent posts are seeded in the #711 schema.
 * Unless a case says otherwise the merged and receiving units both inherit E1, so primary moves need no countersign (#910 covers that);
 * the first case moves a concurrent post into a unit of E2, where only the post's unit changes, never the cost legal entity.
 */
class ConcurrentPostMergeIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(ConcurrentPostMergeIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    // Names of the #1040 design (section 6) and #711 (DD revision 2).
    static final String PRIMARY_IN_UNIT = "primaryInUnit";
    static final String CONCURRENT_COUNT = "concurrentAssignmentCount";
    static final String START_CONCURRENT = "START_CONCURRENT";
    static final String TARGET_ASSIGNMENT_ID = "targetAssignmentId";

    private UUID merged;
    private UUID receiving;

    private void twoUnits() throws Exception {
        merged = seedUnit(topE1);
        receiving = seedUnit(topE1);
    }

    /** A concurrent post of the employee in the unit with the position, open from SINCE (revision 1: the primary also starts on SINCE). */
    private UUID concurrentIn(UUID employee, UUID orgUnit, UUID position) throws SQLException {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            UUID root = AssignmentRoots.create(workforce, stored(employee), AssignmentRoots.CONCURRENT);
            update(workforce, """
                    insert into assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date, revision_number,
                      org_unit_id, position_id, change_channel_kind, created_at, updated_at) values (?, ?, ?, 'CONCURRENT', ?, 1, ?, ?, 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(employee), root, SINCE, stored(orgUnit), position, at, at);
            return root;
        }
    }

    private UUID primaryRootOf(UUID employee) throws SQLException {
        return (UUID) rows("select assignment_id from " + AssignmentRoots.TABLE + " where employee_id = ? and assignment_kind = 'PRIMARY'", stored(employee))
                .get(0).get("assignment_id");
    }

    /** The current version of the root in force on the day, or null. */
    private Map<String, Object> postOn(UUID root, LocalDate day) throws SQLException {
        return inForceOn(rows("select * from assignment_versions where assignment_id = ? and superseded_at is null order by effective_from_date", root), day);
    }

    private List<Map<String, Object>> generated(UUID merge) throws SQLException {
        return rows("select * from personnel_commands where org_structure_command_id = ?", merge);
    }

    private Map<String, Object> generatedFor(UUID merge, UUID root) throws SQLException {
        List<Map<String, Object>> found = rows("select * from personnel_commands where org_structure_command_id = ? and target_assignment_id = ?", merge, root);
        assertThat(found).as("one generated TRANSFER for root " + root).hasSize(1);
        return found.get(0);
    }

    private UUID mergeToday() {
        return merge(merged, receiving, TODAY);
    }

    @Test
    @DisplayName("#1040 完成标准 1、5：只在被并入单元兼职的员工（承接单元在另一法人 E2 下）：合并恰好为该兼职生成一条调岗（target_assignment_kind = CONCURRENT、EFFECTIVE、无会签行、无会签依据行），兼职落到承接单元，主任职不动；不发「员工已调岗」也不发「成本法人已变更」；合并 EFFECTIVE")
    void aConcurrentOnlyEmployeesPostMoves() throws Exception {
        merged = seedUnit(topE1);
        receiving = seedUnit(topE2);
        UUID home = seedUnit(topE1);
        UUID person = inheritingIn(home);
        UUID post = concurrentIn(person, merged, newPosition());
        UUID merge = mergeToday();
        assertThat(statusOf(merge)).isEqualTo(PENDING);

        orgCommands().executeMerge(merge);

        Map<String, Object> move = generatedFor(merge, post);
        assertThat(generated(merge)).hasSize(1);
        assertThat(move.get("command_kind")).isEqualTo(TRANSFER);
        assertThat(move.get("target_assignment_kind")).isEqualTo(AssignmentRoots.CONCURRENT);
        assertThat(move.get("status")).isEqualTo(EFFECTIVE);
        assertThat(rows("select 1 from " + COUNTERSIGNS + " where personnel_command_id = ?", move.get("personnel_command_id"))).isEmpty();
        assertThat(rows("select 1 from " + BASES + " where org_structure_command_id = ?", merge)).isEmpty();
        assertThat(fromStored(postOn(post, TODAY).get("org_unit_id"))).isEqualTo(receiving);
        assertThat(fromStored(postOn(primaryRootOf(person), TODAY).get("org_unit_id"))).isEqualTo(home);
        assertThat(eventsOf(EVENT_TRANSFERRED, (UUID) move.get("personnel_command_id"))).isZero();
        assertThat(legalEntityChangesOf(person)).isZero();
        assertThat(statusOf(merge)).isEqualTo(EFFECTIVE);
        assertThat(orgCommand(merge).get("affected_employee_count")).isEqualTo(1);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1040 完成标准 2：主任职与兼职都在被并入单元的员工：两条调岗，各对应一个根（主任职根、兼职根），都落到承接单元；同一根第二条合并调岗被 uk_personnel_commands_merge_assignment 拒绝")
    void bothPostsOfOneEmployeeMoveOnePerRoot() throws Exception {
        twoUnits();
        UUID person = inheritingIn(merged);
        UUID post = concurrentIn(person, merged, newPosition());
        UUID merge = mergeToday();

        orgCommands().executeMerge(merge);

        UUID primary = primaryRootOf(person);
        assertThat(generated(merge)).hasSize(2);
        assertThat(generatedFor(merge, primary).get("target_assignment_kind")).isEqualTo(AssignmentRoots.PRIMARY);
        assertThat(generatedFor(merge, post).get("target_assignment_kind")).isEqualTo(AssignmentRoots.CONCURRENT);
        assertThat(fromStored(postOn(primary, TODAY).get("org_unit_id"))).isEqualTo(receiving);
        assertThat(fromStored(postOn(post, TODAY).get("org_unit_id"))).isEqualTo(receiving);

        // The finished merge is EFFECTIVE, where the #908 guard refuses any further transfer first; the key is shown on a merge in progress.
        UUID inProgress = seededMergeInProgress();
        UUID other = inheritingIn(seedUnit(topE1));
        UUID otherPost = concurrentIn(other, seedUnit(topE1), newPosition());
        insertMergeTransfer(other, otherPost, inProgress, TODAY.plusDays(1));
        SQLException refused = catchThrowableOfType(SQLException.class, () -> insertMergeTransfer(other, otherPost, inProgress, TODAY.plusDays(2)));
        assertThat((Throwable) refused).as("a second generated TRANSFER of the same root").isNotNull();
        assertThat(refused.getMessage()).contains("uk_personnel_commands_merge_assignment");
    }

    /** A MERGE in progress that needed no countersign (no rows at all), so that the #908 guard admits its transfers. */
    private UUID seededMergeInProgress() throws Exception {
        UUID id = Uuids.timeOrdered(NOW);
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,
                      merge_into_org_unit_id, created_at, created_by, updated_at) values (?, 'MERGE', 'ORG_UNIT', ?, ?, 'PENDING', ?, ?, ?, ?)""",
                    id, stored(seedUnit(topE1)), TODAY, stored(seedUnit(topE1)), at, orgAdmin.userId(), at);
            update(workforce, "update org_structure_commands set status = 'IN_PROGRESS', affected_employee_count = 1, transferred_employee_count = 0, updated_at = ?"
                    + " where org_structure_command_id = ?", at, id);
        }
        return id;
    }

    private void insertMergeTransfer(UUID person, UUID root, UUID merge, LocalDate day) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, target_org_unit_id,
                      target_assignment_id, target_assignment_kind, org_structure_command_id, created_at, created_by, updated_at)
                    values (?, ?, 'TRANSFER', ?, 'PENDING', ?, ?, 'CONCURRENT', ?, ?, ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(person), day, stored(seedUnit(topE1)), root, merge, at, hr.userId(), at);
        }
    }

    /**
     * An obstacle on the concurrent post: an open TRANSFER of that post on the merge day that changes only its position, so the post stays in
     * the merged unit on D and the generated move of the same root on the same day is refused (one open command per concurrent post and day).
     */
    private UUID obstacleOn(UUID person, UUID post, LocalDate day) throws Exception {
        actInSpecialist();
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, person);
        fields.put(COMMAND_KIND, TRANSFER);
        fields.put(EFFECTIVE_DATE, day);
        fields.put(TARGET_ASSIGNMENT_ID, post);
        fields.put(TARGET_POSITION_ID, newPosition());
        UUID obstacle = commands().enter(fields);
        actIn(orgAdmin);
        return obstacle;
    }

    @Test
    @DisplayName("#1040 完成标准 3、4：受影响人数包括只兼职的人；某员工的主任职已调走但兼职被卡住时不算完成、合并停在 IN_PROGRESS（受影响 2、已转 1）、单元不撤销；障碍撤回后重跑，兼职调走，合并 EFFECTIVE（受影响 = 已转 = 2）")
    void theMergeWaitsForEveryConcurrentPost() throws Exception {
        twoUnits();
        LocalDate day = TODAY.plusDays(3);
        UUID both = inheritingIn(merged);
        UUID bothPost = concurrentIn(both, merged, newPosition());
        UUID concurrentOnly = inheritingIn(seedUnit(topE1));
        concurrentIn(concurrentOnly, merged, newPosition());
        UUID obstacle = obstacleOn(both, bothPost, day);
        UUID merge = merge(merged, receiving, day);
        setToday(day);

        catchThrowable(() -> orgCommands().executeMerge(merge));

        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(IN_PROGRESS);
        assertThat(row.get("affected_employee_count")).as("the employee with both posts and the concurrent-only one").isEqualTo(2);
        assertThat(row.get("transferred_employee_count")).as("the concurrent-only one; the other still has a post in the unit").isEqualTo(1);
        assertThat(fromStored(postOn(primaryRootOf(both), day).get("org_unit_id"))).as("the primary moved").isEqualTo(receiving);
        assertThat(fromStored(postOn(bothPost, day).get("org_unit_id"))).as("the concurrent post is still there").isEqualTo(merged);
        assertThat(unitOn(merged, day).get("status")).isNotEqualTo(REVOKED);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();

        actInSpecialist();
        commands().withdraw(obstacle, "synthetic: clears the way");
        actIn(orgAdmin);
        orgCommands().executeMerge(merge);

        row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("transferred_employee_count")).isEqualTo(2);
        assertThat(fromStored(postOn(bothPost, day).get("org_unit_id"))).isEqualTo(receiving);
        assertThat(unitOn(merged, day).get("status")).as("revoked once no assignment is left").isEqualTo(REVOKED);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1040 完成标准 6：合并 IN_PROGRESS 期间，在合并日或之后往被并入单元新增兼职被拒，把别的兼职调进去也被拒；已完成数不变")
    void nothingMovesInWhileInProgressConcurrentPostsIncluded() throws Exception {
        twoUnits();
        LocalDate day = TODAY.plusDays(3);
        UUID both = inheritingIn(merged);
        UUID bothPost = concurrentIn(both, merged, newPosition());
        obstacleOn(both, bothPost, day);
        UUID merge = merge(merged, receiving, day);
        setToday(day);
        catchThrowable(() -> orgCommands().executeMerge(merge));
        assertThat(statusOf(merge)).isEqualTo(IN_PROGRESS);
        Object done = orgCommand(merge).get("transferred_employee_count");
        UUID outsider = inheritingIn(seedUnit(topE1));
        UUID outsidersPost = concurrentIn(outsider, seedUnit(topE1), newPosition());
        actInSpecialist();
        UUID elsewhere = seedUnit(topE1);
        assertThat(commands().enter(fields(EMPLOYEE_ID, outsider, COMMAND_KIND, START_CONCURRENT, EFFECTIVE_DATE, day.plusDays(1),
                TARGET_ORG_UNIT_ID, elsewhere, TARGET_POSITION_ID, newPosition()))).as("the twin: a start into another unit passes").isNotNull();

        for (LocalDate when : List.of(day, day.plusDays(2))) {
            assertRefused(() -> commands().enter(fields(EMPLOYEE_ID, outsider, COMMAND_KIND, START_CONCURRENT, EFFECTIVE_DATE, when,
                    TARGET_ORG_UNIT_ID, merged, TARGET_POSITION_ID, newPosition())));
            assertRefused(() -> commands().enter(fields(EMPLOYEE_ID, outsider, COMMAND_KIND, TRANSFER, EFFECTIVE_DATE, when,
                    TARGET_ASSIGNMENT_ID, outsidersPost, TARGET_ORG_UNIT_ID, merged)));
        }

        assertThat(commands().enter(fields(EMPLOYEE_ID, outsider, COMMAND_KIND, TRANSFER, EFFECTIVE_DATE, day.plusDays(3),
                TARGET_ASSIGNMENT_ID, outsidersPost, TARGET_ORG_UNIT_ID, elsewhere))).as("the twin: moving the post into another unit passes").isNotNull();
        actIn(orgAdmin);
        assertThat(orgCommand(merge).get("transferred_employee_count")).isEqualTo(done);
    }

    @Test
    @DisplayName("#1040 完成标准 7（用户答复 3a）：员工在承接单元已有相同单元与岗位的主任职，被并入单元的同岗位兼职随合并调过去后，两条任职都在（什么都不结束）")
    void theSamePostHeldTwiceKeepsBoth() throws Exception {
        twoUnits();
        UUID position = newPosition();
        UUID person = employedAs(receiving, REGULAR, position);
        UUID post = concurrentIn(person, merged, position);
        UUID merge = mergeToday();

        orgCommands().executeMerge(merge);

        assertThat(fromStored(postOn(post, TODAY).get("org_unit_id"))).isEqualTo(receiving);
        assertThat(fromStored(postOn(primaryRootOf(person), TODAY).get("org_unit_id"))).isEqualTo(receiving);
        assertThat(rows("select 1 from assignment_versions where employee_id = ? and org_unit_id = ? and position_id = ? and effective_to_date is null and superseded_at is null",
                stored(person), stored(receiving), position)).as("both posts, same unit and position").hasSize(2);
        assertThat(statusOf(merge)).isEqualTo(EFFECTIVE);
    }

    @Test
    @DisplayName("#1040 完成标准 8（第 6 节）：合并预演仍每人一项；只兼职的人 primaryInUnit = false、concurrentAssignmentCount ≥ 1、needsAttributionCountersign = false、前后法人为空；主任职与兼职都在的人 primaryInUnit = true、concurrentAssignmentCount = 1")
    void thePreviewShowsConcurrentPosts() throws Exception {
        merged = seedUnit(topE1);
        receiving = seedUnit(topE2);
        UUID concurrentOnly = inheritingIn(seedUnit(topE1));
        concurrentIn(concurrentOnly, merged, newPosition());
        UUID both = inheritingIn(merged);
        concurrentIn(both, merged, newPosition());

        MockHttpServletResponse response = mvc.perform(get(PREVIEW_MERGE_PATH, merged).param("mergeIntoOrgUnitId", text(receiving))
                .param("effectiveDate", TODAY.plusDays(3).toString()).with(session(orgAdmin.session()))).andReturn().getResponse();

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        List<Map<String, Object>> items = JsonPath.read(response.getContentAsString(), "$.items");
        assertThat(items).extracting(item -> item.get("employeeId")).containsExactlyInAnyOrder(text(concurrentOnly), text(both));
        Map<String, Object> onlyConcurrent = items.stream().filter(item -> text(concurrentOnly).equals(item.get("employeeId"))).findFirst().orElseThrow();
        assertThat(onlyConcurrent.get(PRIMARY_IN_UNIT)).isEqualTo(false);
        assertThat(((Number) onlyConcurrent.get(CONCURRENT_COUNT)).intValue()).as("the one seeded post").isEqualTo(1);
        assertThat(onlyConcurrent.get("needsAttributionCountersign")).isEqualTo(false);
        assertThat(onlyConcurrent.get("previousLegalEntityId")).isNull();
        assertThat(onlyConcurrent.get("newLegalEntityId")).isNull();
        Map<String, Object> bothItem = items.stream().filter(item -> text(both).equals(item.get("employeeId"))).findFirst().orElseThrow();
        assertThat(bothItem.get(PRIMARY_IN_UNIT)).isEqualTo(true);
        assertThat(((Number) bothItem.get(CONCURRENT_COUNT)).intValue()).isEqualTo(1);
    }

    /** The employee is no longer employed on TODAY by the employee versions (the versions decide employment), whatever the assignment versions still say. */
    private void notEmployedSinceYesterday(UUID person) throws SQLException {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        LocalDate yesterday = TODAY.minusDays(1);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employee_versions set effective_to_date = ?, closed_at = ?, updated_at = ? where employee_id = ? and effective_to_date is null and superseded_at is null",
                    yesterday, at, at, stored(person));
            update(workforce, """
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                      is_on_unpaid_leave, is_fee_earner, change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, 0, 'SEPARATED', false, false, 'MIGRATION', ?, ?)""", Uuids.timeOrdered(NOW), stored(person), TODAY, at, at);
            update(workforce, "update employees set employment_status = 'SEPARATED', separated_at = ? where id = ?", at, stored(person));
        }
    }

    @Test
    @DisplayName("#1040 裁定 6605327850 验收 (4)：主任职与兼职都在被并入单元的员工与只有主任职的员工：共三条调岗，受影响人数与已转人数都按员工数为 2（不按任职数）")
    void twoPostsOfOneEmployeeCountAsOneEmployee() throws Exception {
        twoUnits();
        UUID both = inheritingIn(merged);
        UUID bothPost = concurrentIn(both, merged, newPosition());
        UUID primaryOnly = inheritingIn(merged);
        UUID merge = mergeToday();

        orgCommands().executeMerge(merge);

        assertThat(generated(merge)).as("one TRANSFER per assignment").hasSize(3);
        assertThat(generatedFor(merge, primaryRootOf(both))).isNotNull();
        assertThat(generatedFor(merge, bothPost)).isNotNull();
        assertThat(generatedFor(merge, primaryRootOf(primaryOnly))).isNotNull();
        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("affected_employee_count")).as("employees, not assignments").isEqualTo(2);
        assertThat(row.get("transferred_employee_count")).isEqualTo(2);
    }

    @Test
    @DisplayName("#1040 裁定 6605327850 验收 (8)：合并日已不在职的员工留在被并入单元的兼职不被调走、不阻塞：没有为它生成调岗，受影响人数不含此人，合并 EFFECTIVE")
    void aConcurrentPostOfSomeoneNotEmployedOnTheDateIsNotMovedAndDoesNotBlock() throws Exception {
        twoUnits();
        UUID employed = inheritingIn(merged);
        UUID employedPost = concurrentIn(employed, merged, newPosition());
        UUID gone = inheritingIn(seedUnit(topE1));
        UUID gonePost = concurrentIn(gone, merged, newPosition());
        notEmployedSinceYesterday(gone);
        UUID merge = mergeToday();

        orgCommands().executeMerge(merge);

        assertThat(rows("select 1 from personnel_commands where org_structure_command_id = ? and target_assignment_id = ?", merge, gonePost)).as("nothing generated for the post").isEmpty();
        assertThat(generatedFor(merge, employedPost)).isNotNull();
        assertThat(fromStored(postOn(gonePost, TODAY).get("org_unit_id"))).as("the post is left where it was").isEqualTo(merged);
        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("affected_employee_count")).as("only the employed one").isEqualTo(1);
        assertThat(row.get("status")).as("their post does not hold the merge back").isEqualTo(EFFECTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
    }
}
