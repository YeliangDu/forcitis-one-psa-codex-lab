package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EVENT_TRANSFERRED;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PROBATION;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULARIZE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TRANSFER;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #906 IMP-1030-ORG-F2a (ADR-0068 item 4 and the merge notes of its attachment): a merge is rehearsed first (the affected employees, nothing
 * written), then run by the due-date step (agreed name executeMerge): PENDING to IN_PROGRESS with the number of employed people of that moment,
 * one TRANSFER per person in its own transaction that refers to the merge, the counter grows, a rerun after an interruption skips people
 * already moved (uk_personnel_commands_merge_employee), and only when all are moved the merged unit is revoked in the same transaction and the
 * merge is EFFECTIVE. The interruption is made by a real obstacle: one person has an open personnel command on the merge day, so the
 * person's generated transfer is refused by the same-day rule (ADR-0068 item 1.5) until that command is withdrawn. People keep their individually
 * attributed legal entity, so no countersign applies (F4a).
 */
class OrgUnitMergeIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgUnitMergeIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private UUID merged;
    private UUID receiving;

    private List<UUID> peopleInTheMergedUnit(int count, String phase) throws Exception {
        merged = seedUnit(null);
        receiving = seedUnit(null);
        List<UUID> people = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            people.add(employedIn(merged, phase, true));
        }
        return people;
    }

    private UUID enterMerge(LocalDate day) {
        return unitCommand(MERGE, merged, day, MERGE_INTO_ORG_UNIT_ID, receiving);
    }

    private List<Map<String, Object>> transfersOf(UUID merge) throws Exception {
        return rows("select * from personnel_commands where org_structure_command_id = ? order by employee_id", merge);
    }

    /** One-design ruling 2026-10-30 #906: completing a merge publishes OrgUnitMerged only, not OrgUnitDissolved. */
    private void assertNoDissolvedEvent(UUID merge) throws Exception {
        assertThat(eventsOf(EVENT_ORG_UNIT_DISSOLVED, merge)).isZero();
        assertThat(eventsOf(EVENT_ORG_UNIT_DISSOLVED, merged)).isZero();
    }

    private long writtenRows() throws Exception {
        return orgCommandCount() + (long) rows("select count(*) as n from personnel_commands").get(0).get("n") + unitVersions(merged).size() + unitVersions(receiving).size()
                + (long) rows("select count(*) as n from state_transitions").get(0).get("n") + (long) rows("select count(*) as n from assignment_versions").get(0).get("n");
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条「合并：先预演（返回受影响员工清单，不落库）」：预演返回被并入单元在那天在职的员工（已离职的不在内、别的单元的人不在内），不写命令、版本、任职、状态转换")
    void thePreviewListsTheAffectedAndWritesNothing() throws Exception {
        List<UUID> people = peopleInTheMergedUnit(3, REGULAR);
        UUID leaver = employedIn(merged, REGULAR, true);
        employedIn(receiving, REGULAR, true);
        actInSpecialist();
        separate(leaver, TODAY.minusDays(1));
        actIn(orgAdmin);
        long before = writtenRows();

        List<UUID> affected = orgCommands().previewMerge(merged, receiving, TODAY);

        assertThat(affected).containsExactlyInAnyOrderElementsOf(people);
        assertThat(writtenRows()).as("a rehearsal writes nothing").isEqualTo(before);
        assertThat(orgCommands().previewMerge(seedUnit(null), receiving, TODAY)).as("an empty unit has nobody to move").isEmpty();
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 ck_org_structure_commands_merge_columns、_merge_not_self、第 4 条：合并必须带承接单元；不能并入自己；承接与被并入单元都必须存在；合并不带名称等其余内容；被拒不留命令行")
    void aBadMergeIsRefused() throws Exception {
        peopleInTheMergedUnit(1, REGULAR);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(MERGE, merged, TODAY));
        assertRefused(() -> unitCommand(MERGE, merged, TODAY, MERGE_INTO_ORG_UNIT_ID, merged));
        assertRefused(() -> unitCommand(MERGE, merged, TODAY, MERGE_INTO_ORG_UNIT_ID, UUID.randomUUID()));
        assertRefused(() -> unitCommand(MERGE, UUID.randomUUID(), TODAY, MERGE_INTO_ORG_UNIT_ID, receiving));
        assertRefused(() -> unitCommand(MERGE, merged, TODAY, MERGE_INTO_ORG_UNIT_ID, receiving, TARGET_NAME, "Also renamed"));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, merged, TODAY, TARGET_NAME, "Not a merge", MERGE_INTO_ORG_UNIT_ID, receiving));

        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件列说明：合并命令录入后待生效，执行前不写任何版本和任职；同一被并入单元同一天只能有一条未生效命令（合并与调整属性互相挡）")
    void enteringAMergeWritesNoVersionsAndOccupiesTheDay() throws Exception {
        List<UUID> people = peopleInTheMergedUnit(2, REGULAR);
        LocalDate day = TODAY.plusDays(6);
        long versions = unitVersions(merged).size() + unitVersions(receiving).size();

        UUID merge = enterMerge(day);

        assertThat(orgCommand(merge).get("status")).isEqualTo(PENDING);
        assertThat(orgCommand(merge).get("command_kind")).isEqualTo(MERGE);
        assertThat(orgCommand(merge).get("affected_employee_count")).as("one-design ruling 2026-10-30 #906: counts are null while PENDING").isNull();
        assertThat(orgCommand(merge).get("transferred_employee_count")).isNull();
        assertThat(fromStored(orgCommand(merge).get("target_org_unit_id"))).isEqualTo(merged);
        assertThat(fromStored(orgCommand(merge).get("merge_into_org_unit_id"))).isEqualTo(receiving);
        assertThat(unitVersions(merged).size() + unitVersions(receiving).size()).isEqualTo(versions);
        assertThat(transfersOf(merge)).isEmpty();
        assertThat(unitOfEmployee(people.get(0), day)).isEqualTo(merged);
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, merged, day, TARGET_NAME, "Same day as the merge"));
        assertRefused(() -> enterMerge(day));
        assertThat(unitCommand(SET_ATTRIBUTES, merged, day.minusDays(1), TARGET_NAME, "Renamed before the merge")).as("an earlier day").isNotNull();
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条与附件列说明（执行）：合并执行：先转 IN_PROGRESS 并写受影响人数（执行时点的在职名单），逐人生成调岗命令引用本合并，全部转完后同一事务撤销被并入单元、转 EFFECTIVE；进度两列相等，每人落在承接单元、岗位沿用，事件按人发「员工已调岗」各一次并发一次「组织单元已合并」")
    void executingAMove() throws Exception {
        List<UUID> people = peopleInTheMergedUnit(3, REGULAR);
        UUID leaver = employedIn(merged, REGULAR, true);
        actInSpecialist();
        separate(leaver, TODAY.minusDays(1));
        actIn(orgAdmin);
        UUID merge = enterMerge(TODAY);
        List<UUID> positions = new ArrayList<>();
        for (UUID person : people) {
            positions.add(positionHeldBy(person, TODAY));
        }

        orgCommands().executeMerge(merge);

        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("affected_employee_count")).isEqualTo(3);
        assertThat(row.get("transferred_employee_count")).isEqualTo(3);
        assertThat(row.get("applied_at")).isEqualTo(NOW);
        List<Map<String, Object>> steps = transitions(AGGREGATE_ORG_COMMAND, merge);
        assertLawfulMergeLifecycle(steps, EFFECTIVE);
        assertThat(steps).as("through IN_PROGRESS").anyMatch(step -> IN_PROGRESS.equals(step.get("to_status")));
        List<Map<String, Object>> transfers = transfersOf(merge);
        assertThat(transfers).hasSize(3).allSatisfy(transfer -> {
            assertThat(transfer.get("command_kind")).isEqualTo(TRANSFER);
            assertThat(transfer.get("status")).isEqualTo(EFFECTIVE);
            assertThat(fromStored(transfer.get("target_org_unit_id"))).isEqualTo(receiving);
            assertThat(transfer.get("effective_date")).isEqualTo(TODAY);
            assertThat(eventsOf(EVENT_TRANSFERRED, (UUID) transfer.get("personnel_command_id"))).as("one transfer event per person").isEqualTo(1);
        });
        assertThat(transfers.stream().map(transfer -> fromStored(transfer.get("employee_id")))).containsExactlyInAnyOrderElementsOf(people);
        for (int i = 0; i < people.size(); i++) {
            assertThat(unitOfEmployee(people.get(i), TODAY)).isEqualTo(receiving);
            assertThat(unitOfEmployee(people.get(i), TODAY.minusDays(1))).isEqualTo(merged);
            assertThat(positionHeldBy(people.get(i), TODAY)).as("the position is copied").isEqualTo(positions.get(i));
        }
        assertThat(rows("select 1 from personnel_commands where org_structure_command_id = ? and employee_id = ?", merge, stored(leaver))).as("the leaver is not moved").isEmpty();
        assertThat(unitOn(merged, TODAY).get("status")).as("the merged unit is revoked with the last transfer").isEqualTo(REVOKED);
        assertThat(unitOn(merged, TODAY.minusDays(1)).get("status")).isEqualTo(ACTIVE);
        assertThat(unitOn(receiving, TODAY).get("status")).isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
        assertNoDissolvedEvent(merge);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件列说明「中断后重跑按 uk_personnel_commands_merge_employee 跳过已转的人」、ck_merge_progress、触发器「IN_PROGRESS 不可撤回」：一人被卡住时合并停在 IN_PROGRESS（受影响 3、已转 2、不是 EFFECTIVE、被并入单元未撤销、不发「已合并」），不可撤回；障碍解除后重跑只转剩下的人，不重复生成，转完才 EFFECTIVE 并撤销单元；再跑一次无变化")
    void aRerunSkipsThoseAlreadyMovedAndOnlyFinishedWhenAllAre() throws Exception {
        merged = seedUnit(null);
        receiving = seedUnit(null);
        UUID first = employedIn(merged, REGULAR, true);
        UUID second = employedIn(merged, REGULAR, true);
        UUID stuck = employedIn(merged, PROBATION, true);
        LocalDate day = TODAY.plusDays(5);
        actInSpecialist();
        UUID obstacle = enter(stuck, REGULARIZE, day);
        actIn(orgAdmin);
        UUID merge = enterMerge(day);
        setToday(day);

        catchThrowable(() -> orgCommands().executeMerge(merge));

        Map<String, Object> halfway = orgCommand(merge);
        assertThat(halfway.get("status")).isEqualTo(IN_PROGRESS);
        assertThat(halfway.get("affected_employee_count")).isEqualTo(3);
        assertThat(halfway.get("transferred_employee_count")).as("two of three were moved").isEqualTo(2);
        assertThat(halfway.get("applied_at")).isNull();
        assertThat(transfersOf(merge)).hasSize(2).noneMatch(transfer -> stuck.equals(fromStored(transfer.get("employee_id"))));
        assertThat(unitOfEmployee(stuck, day)).isEqualTo(merged);
        assertThat(unitOfEmployee(first, day)).isEqualTo(receiving);
        assertThat(unitOn(merged, day).get("status")).as("not revoked while somebody is still in it").isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();
        assertRefused(() -> orgCommands().withdraw(merge, "synthetic: too late"));
        assertThat(orgCommand(merge).get("status")).as("an IN_PROGRESS merge cannot be withdrawn").isEqualTo(IN_PROGRESS);
        assertThat(orgCommand(merge).get("withdrawn_by")).isNull();

        actInSpecialist();
        commands().withdraw(obstacle, "synthetic: clears the way");
        actIn(orgAdmin);
        orgCommands().executeMerge(merge);

        Map<String, Object> done = orgCommand(merge);
        assertThat(done.get("status")).isEqualTo(EFFECTIVE);
        assertThat(done.get("affected_employee_count")).isEqualTo(3);
        assertThat(done.get("transferred_employee_count")).isEqualTo(3);
        List<Map<String, Object>> transfers = transfersOf(merge);
        assertThat(transfers).as("nobody is moved twice").hasSize(3);
        assertThat(transfers.stream().map(transfer -> fromStored(transfer.get("employee_id")))).containsExactlyInAnyOrder(first, second, stuck);
        assertThat(unitOfEmployee(stuck, day)).isEqualTo(receiving);
        assertThat(unitOn(merged, day).get("status")).isEqualTo(REVOKED);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
        assertNoDissolvedEvent(merge);
        for (Map<String, Object> transfer : transfers) {
            assertThat(eventsOf(EVENT_TRANSFERRED, (UUID) transfer.get("personnel_command_id"))).isEqualTo(1);
        }
        assertLawfulMergeLifecycle(transitions(AGGREGATE_ORG_COMMAND, merge), EFFECTIVE);

        catchThrowable(() -> orgCommands().executeMerge(merge));
        assertThat(transfersOf(merge)).as("a rerun of a finished merge moves nobody again").hasSize(3);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
        assertNoDissolvedEvent(merge);
    }

    @Test
    @DisplayName("#906 one-design ruling 2026-10-30 #906（树规则）：被并入单元在那天有 ACTIVE 下级单元时合并被拒（人事先调整下级的上级）；下级调走后可以合并；不留命令行")
    void aUnitWithActiveChildrenCannotBeMerged() throws Exception {
        peopleInTheMergedUnit(0, REGULAR);
        UUID child = seedUnit(merged);
        long commands = orgCommandCount();

        assertRefused(() -> enterMerge(TODAY));
        assertRefused(() -> enterMerge(TODAY.plusDays(4)));

        assertThat(orgCommandCount()).isEqualTo(commands);
        unitCommand(REPARENT, child, TODAY, TARGET_PARENT_ORG_UNIT_ID, receiving);
        assertThat(enterMerge(TODAY)).as("the children were reparented first").isNotNull();
    }

    @Test
    @DisplayName("#906 one-design ruling 2026-10-30 #906（按整条时间线校验，rv917x M1 之后的细化）第 3 条、用户决定 2026-10-30（「推荐」：有在职下级单元的单元不能合并）：已录入合并之后，把下级单元挂到被并入单元之下（调整上级在合并日前后、或新建）在录入时被拒")
    void noChildMayBeAddedBelowAUnitWithAMergeEntered() throws Exception {
        peopleInTheMergedUnit(0, REGULAR);
        UUID mover = seedUnit(null);
        enterMerge(TODAY.plusDays(4));
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(REPARENT, mover, TODAY.plusDays(2), TARGET_PARENT_ORG_UNIT_ID, merged));
        assertRefused(() -> unitCommand(REPARENT, mover, TODAY.plusDays(6), TARGET_PARENT_ORG_UNIT_ID, merged));
        assertRefused(() -> createUnit(TODAY.plusDays(3), TARGET_PARENT_ORG_UNIT_ID, merged));

        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#906 one-design ruling 2026-10-30 #906（按整条时间线校验，rv917x M1 之后的细化）第 4 条、用户决定 2026-10-30（「推荐」）：执行时再校验：录入合并之后（例如同步写入）有下级单元挂到被并入单元之下，executeMerge 被拒，合并仍 PENDING、没有调岗、被并入单元不撤销")
    void executionChecksTheChildrenAgain() throws Exception {
        List<UUID> people = peopleInTheMergedUnit(2, REGULAR);
        UUID child = seedUnit(receiving);
        UUID merge = enterMerge(TODAY);
        java.time.OffsetDateTime at = java.time.OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC);
        try (java.sql.Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update org_unit_versions set effective_to_date = ?, closed_at = ?, updated_at = ? where org_unit_id = ? and effective_to_date is null", TODAY, at, at, stored(child));
            update(workforce, """
                    insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, parent_org_unit_id, status, region_kind,
                      is_delivery_unit, created_at, updated_at) values (?, ?, ?, ?, ?, 'ACTIVE', 'HEADQUARTERS', false, ?, ?)""",
                    com.forcitis.one.kernel.Uuids.timeOrdered(NOW), stored(child), TODAY, seededName(child), stored(merged), at, at);
        }

        assertRefused(() -> orgCommands().executeMerge(merge));

        assertThat(orgCommand(merge).get("status")).isEqualTo(PENDING);
        assertThat(transfersOf(merge)).isEmpty();
        assertThat(unitOfEmployee(people.get(0), TODAY)).isEqualTo(merged);
        assertThat(unitOn(merged, TODAY).get("status")).isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 ck_merge_progress（0 ≤ 已转 ≤ 受影响）：被并入单元已没有在职员工时，执行得受影响 0、已转 0，直接 EFFECTIVE 并撤销被并入单元，不生成调岗，仍发一次「组织单元已合并」")
    void aMergeOfAnEmptyUnit() throws Exception {
        peopleInTheMergedUnit(0, REGULAR);
        UUID merge = enterMerge(TODAY);

        orgCommands().executeMerge(merge);

        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("affected_employee_count")).isEqualTo(0);
        assertThat(row.get("transferred_employee_count")).isEqualTo(0);
        assertThat(transfersOf(merge)).isEmpty();
        assertThat(unitOn(merged, TODAY).get("status")).isEqualTo(REVOKED);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
        assertNoDissolvedEvent(merge);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 1 条第 8 项（到日作业）与第 6 条类推：生效日未到的合并不能执行（被拒，仍 PENDING，没有调岗）；待生效的合并可以撤回，撤回后不能再执行，不留任何调岗或版本")
    void aMergeNeedsItsDayAndAPendingOneCanBeWithdrawn() throws Exception {
        List<UUID> people = peopleInTheMergedUnit(2, REGULAR);
        LocalDate day = TODAY.plusDays(3);
        UUID merge = enterMerge(day);

        assertRefused(() -> orgCommands().executeMerge(merge));

        assertThat(orgCommand(merge).get("status")).isEqualTo(PENDING);
        assertThat(transfersOf(merge)).isEmpty();
        orgCommands().withdraw(merge, "synthetic: not this month");
        assertThat(orgCommand(merge).get("status")).isEqualTo(WITHDRAWN);
        setToday(day);
        assertRefused(() -> orgCommands().executeMerge(merge));
        assertThat(orgCommand(merge).get("status")).isEqualTo(WITHDRAWN);
        assertThat(transfersOf(merge)).isEmpty();
        assertThat(unitOfEmployee(people.get(0), day)).isEqualTo(merged);
        assertThat(unitOn(merged, day).get("status")).isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();
        assertThat(enterMerge(day)).as("the day is free again").isNotNull();
    }
}
