package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.ASSIGNMENT_VERSIONS;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PROBATION;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULARIZE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #907, one-design ruling https://github.com/FORCITIS/forcitis-one-psa/issues/907#issuecomment-6596302513 and the user's answer 「a」 (recorded
 * by lead on #907): a merge M of unit X into Y effective D and the people who arrive in X on or after D.
 * Part 1 (technical): while M is open, a personnel command that places someone in X on or after D is refused at entry; the day before is
 * allowed and M moves that person; after M is withdrawn the same command is accepted.
 * Part 2 (option a): an arrival on or after D already on record blocks M at entry, at start and at finish; HR clears it (withdraws it or
 * re-enters it into Y) and M then runs; the system never rewrites the blocking command.
 * Not here yet: that the refusals and the merge preview name the blocking employees and their command ids (B1-B3) and the named conflict error
 * of part 1: the field and error names come from the implementer and are asked of lead; until then the refusals are checked as refusals.
 */
class OrgMergeLaterArrivalIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgMergeLaterArrivalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate D = TODAY.plusDays(6);

    private UUID merged;
    private UUID receiving;
    private UUID elsewhere;

    private UUID world() throws Exception {
        merged = seedUnit(null);
        receiving = seedUnit(null);
        elsewhere = seedUnit(null);
        return employedIn(merged, REGULAR, true);
    }

    private UUID enterMerge(LocalDate day) {
        return unitCommand(MERGE, merged, day, MERGE_INTO_ORG_UNIT_ID, receiving);
    }

    private List<Map<String, Object>> transfersOf(UUID merge) throws Exception {
        return rows("select * from personnel_commands where org_structure_command_id = ? order by employee_id", merge);
    }

    /** Moves the clock to the merge day; the sessions signed in before are past their 24 h lifetime then (RULING-LEAD-20261103-07). */
    private void onTheMergeDay() throws Exception {
        setToday(D);
        if (hr != null) {
            hr = new Staff(hr.employeeId(), signIn(hr.employeeId()));
        }
        orgAdmin = new Staff(orgAdmin.employeeId(), signIn(orgAdmin.employeeId()));
        actIn(orgAdmin);
    }

    /**
     * Someone is in X from the given day without any personnel command, as a write outside the command path would leave it (the precedent is
     * OrgUnitMergeIT.executionChecksTheChildrenAgain): the open assignment row is closed on that day and a MIGRATION row in X follows. It is the
     * only way to have an arrival after an entered merge, since part 1 refuses one entered through the commands.
     */
    private void arrivesOutsideTheCommands(UUID person, LocalDate from) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            Object position = query(workforce, "select position_id from assignment_versions where employee_id = ? and effective_to_date is null and superseded_at is null",
                    stored(person)).get(0).get("position_id");
            update(workforce, "update assignment_versions set effective_to_date = ?, closed_at = ?, updated_at = ? where employee_id = ? and effective_to_date is null and superseded_at is null",
                    from, at, at, stored(person));
            update(workforce, """
                    insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                      change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(person), from, stored(merged), position, at, at);
        }
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 1 条（技术裁定）：合并待生效时，把人调入被并入单元、生效日为合并日或其后一天的调岗在录入时被拒，不留命令行")
    void anArrivalOnOrAfterTheMergeDayIsRefusedWhileTheMergeIsOpen() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        enterMerge(D);
        actInSpecialist();

        assertRefused(() -> transfer(mover, D, merged));
        assertRefused(() -> transfer(mover, D.plusDays(1), merged));

        assertThat(commandCount(mover)).isZero();
        assertThat(unitOfEmployee(mover, D)).isEqualTo(elsewhere);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 1 条：合并待生效时，调入被并入单元、生效日为合并日前一天的调岗被接受，合并的预演列出此人，执行时把此人转到承接单元")
    void anArrivalTheDayBeforeIsAcceptedAndMoved() throws Exception {
        UUID stayer = world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        UUID merge = enterMerge(D);
        actInSpecialist();

        UUID arrival = transfer(mover, D.minusDays(1), merged);

        assertThat(command(arrival).get("status")).isEqualTo(PENDING);
        actIn(orgAdmin);
        assertThat(orgCommands().previewMerge(merged, receiving, D)).containsExactlyInAnyOrder(stayer, mover);
        onTheMergeDay();
        orgCommands().executeMerge(merge);
        assertThat(orgCommand(merge).get("status")).isEqualTo(EFFECTIVE);
        assertThat(transfersOf(merge).stream().map(transfer -> fromStored(transfer.get("employee_id")))).containsExactlyInAnyOrder(stayer, mover);
        assertThat(unitOfEmployee(mover, D)).isEqualTo(receiving);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 1 条：合并撤回后，原先被拒的调入（合并日当天）被接受")
    void afterTheMergeIsWithdrawnTheArrivalIsAccepted() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        UUID merge = enterMerge(D);
        actInSpecialist();
        assertRefused(() -> transfer(mover, D, merged));
        actIn(orgAdmin);

        orgCommands().withdraw(merge, "synthetic: the merge is off");
        actInSpecialist();

        assertThat(orgCommand(merge).get("status")).isEqualTo(WITHDRAWN);
        assertThat(transfer(mover, D, merged)).isNotNull();
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：先录了调入被并入单元、生效日在合并日后 10 天的调岗，再录合并被拒，不留合并命令行；该调岗命令行与其任职版本在拒绝前后不变")
    void aLaterArrivalOnRecordBlocksTheEntry() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID blocking = transfer(mover, D.plusDays(10), merged);
        Map<String, Object> blockingBefore = command(blocking);
        List<Map<String, Object>> versionsBefore = allVersions(ASSIGNMENT_VERSIONS, mover);
        actIn(orgAdmin);
        long commands = orgCommandCount();

        assertRefused(() -> enterMerge(D));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(command(blocking)).as("the system does not rewrite or retarget the blocking command").isEqualTo(blockingBefore);
        assertThat(allVersions(ASSIGNMENT_VERSIONS, mover)).isEqualTo(versionsBefore);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：倒签的合并（合并日在过去，有人自合并日后第 3 天起已在被并入单元）录入被拒；没有这样的人的另一单元同日倒签合并可以录入")
    void aBackDatedMergeWithSomeoneAlreadyArrivedIsRefusedAtEntry() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        LocalDate past = TODAY.minusDays(10);
        actInSpecialist();
        UUID arrival = transfer(mover, past.plusDays(3), merged);
        Map<String, Object> arrivalBefore = command(arrival);
        actIn(orgAdmin);
        long commands = orgCommandCount();

        assertRefused(() -> enterMerge(past));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(command(arrival)).isEqualTo(arrivalBefore);
        UUID quiet = seedUnit(null);
        employedIn(quiet, REGULAR, true);
        assertThat(unitCommand(MERGE, quiet, past, MERGE_INTO_ORG_UNIT_ID, receiving)).as("the refusal is the arrival's, not the back date's").isNotNull();
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：开始时再校验：合并录入后有人在合并日后第 3 天起在被并入单元（不经人事命令写入），到日执行被拒：仍 PENDING、没有调岗、原有人员未动")
    void theStartChecksTheArrivalsAgain() throws Exception {
        UUID stayer = world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        UUID merge = enterMerge(D);
        arrivesOutsideTheCommands(mover, D.plusDays(3));
        onTheMergeDay();

        assertRefused(() -> orgCommands().executeMerge(merge));

        assertThat(orgCommand(merge).get("status")).isEqualTo(PENDING);
        assertThat(transfersOf(merge)).isEmpty();
        assertThat(unitOfEmployee(stayer, D)).isEqualTo(merged);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：收尾时再校验：合并执行中（一人被同日命令卡住）出现合并日后到来的人，障碍解除后收尾被拒：仍 IN_PROGRESS、被并入单元不撤销、不发「组织单元已合并」")
    void theFinishChecksTheArrivalsAgain() throws Exception {
        world();
        UUID stuck = employedIn(merged, PROBATION, true);
        UUID mover = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID obstacle = enter(stuck, REGULARIZE, D);
        actIn(orgAdmin);
        UUID merge = enterMerge(D);
        onTheMergeDay();
        catchThrowable(() -> orgCommands().executeMerge(merge));
        assertThat(orgCommand(merge).get("status")).as("halfway, as in OrgUnitMergeIT").isEqualTo(IN_PROGRESS);
        arrivesOutsideTheCommands(mover, D.plusDays(3));
        actInSpecialist();
        commands().withdraw(obstacle, "synthetic: clears the way");
        actIn(orgAdmin);

        assertRefused(() -> orgCommands().executeMerge(merge));

        assertThat(orgCommand(merge).get("status")).isEqualTo(IN_PROGRESS);
        assertThat(orgCommand(merge).get("applied_at")).isNull();
        assertThat(unitOn(merged, D).get("status")).isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：人事撤回挡路的调岗后，合并可以录入并执行到 EFFECTIVE")
    void withdrawingTheBlockerLetsTheMergeRun() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID blocking = transfer(mover, D.plusDays(10), merged);
        actIn(orgAdmin);
        assertRefused(() -> enterMerge(D));
        actInSpecialist();

        commands().withdraw(blocking, "synthetic: cleared for the merge");
        actIn(orgAdmin);
        UUID merge = enterMerge(D);
        onTheMergeDay();
        orgCommands().executeMerge(merge);

        assertThat(orgCommand(merge).get("status")).isEqualTo(EFFECTIVE);
        assertThat(unitOn(merged, D).get("status")).isEqualTo(REVOKED);
        assertThat(command(blocking).get("status")).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：人事把挡路的调岗撤回、改录为调入承接单元后，合并可以录入并执行到 EFFECTIVE；改录的调岗不被合并改动，仍待生效、目标仍是承接单元")
    void reEnteringTheBlockerIntoTheReceivingUnitLetsTheMergeRun() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID blocking = transfer(mover, D.plusDays(10), merged);
        actIn(orgAdmin);
        assertRefused(() -> enterMerge(D));
        actInSpecialist();

        commands().withdraw(blocking, "synthetic: re-entered into the receiving unit");
        UUID reEntered = transfer(mover, D.plusDays(10), receiving);
        Map<String, Object> reEnteredBefore = command(reEntered);
        actIn(orgAdmin);
        UUID merge = enterMerge(D);
        onTheMergeDay();
        orgCommands().executeMerge(merge);

        assertThat(orgCommand(merge).get("status")).isEqualTo(EFFECTIVE);
        assertThat(transfersOf(merge).stream().map(transfer -> fromStored(transfer.get("employee_id")))).doesNotContain(mover);
        assertThat(command(reEntered)).isEqualTo(reEnteredBefore);
        assertThat(unitOfEmployee(mover, D.plusDays(10))).isEqualTo(receiving);
    }
}
