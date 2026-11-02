package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.ACTIVE;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.EVENT_MERGED;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.IN_PROGRESS;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.REVOKED;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TRANSFER;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #910 acceptance part (#909 F4a), ADR-0073 守护 1 (line 60) and note section 5 rules 4 and 6, user answer QF1 (a) of 2026-10-30: at
 * execution of a countersigned merge each person's before and after is computed again; equal to the basis row (or unchanged and not in
 * the basis) gives an exempt TRANSFER that is counted; different gives a TRANSFER that waits for its own countersign and is not counted;
 * the merge is EFFECTIVE only when everybody is counted; a person whose own countersign is rejected stays, the merge stays IN_PROGRESS
 * until another effective command moves the person out; EmployeeLegalEntityChanged goes out once per changed person, through the transfer.
 */
class OrgCountersignMergeIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgCountersignMergeIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate DAY = TODAY.plusDays(5);

    private UUID merged;
    private UUID receiving;
    private UUID inheritingA;
    private UUID inheritingB;
    private UUID own;

    /** A merge of a unit inheriting E1 into a unit inheriting E2: two inheriting people (basis E1 → E2) and one with an own attribution. */
    private UUID enteredMerge() throws Exception {
        merged = seedUnit(topE1);
        receiving = seedUnit(topE2);
        inheritingA = inheritingIn(merged);
        inheritingB = inheritingIn(merged);
        own = employedIn(merged, REGULAR, true);
        UUID merge = merge(merged, receiving, DAY);
        assertThat(statusOf(merge)).isEqualTo(AWAITING);
        assertThat(basis(merge)).containsOnlyKeys(inheritingA, inheritingB);
        return merge;
    }

    private UUID countersignedMerge() throws Exception {
        UUID merge = enteredMerge();
        approve(headE1, merge, KIND_CHANGE, e1);
        approve(headE2, merge, KIND_CHANGE, e2);
        assertThat(statusOf(merge)).isEqualTo(PENDING);
        return merge;
    }

    private UUID personnelRequest(UUID personnelCommand, UUID entity) throws SQLException {
        List<Map<String, Object>> found = rows("select attribution_countersign_id from " + COUNTERSIGNS
                + " where personnel_command_id = ? and request_kind = ? and legal_entity_id = ? order by request_sequence_number desc", personnelCommand, KIND_CHANGE, entity);
        assertThat(found).as("a personnel countersign row for " + personnelCommand + " and " + entity).isNotEmpty();
        return (UUID) found.get(0).get("attribution_countersign_id");
    }

    private UUID transferOf(UUID merge, UUID employee) throws SQLException {
        return (UUID) generatedTransfers(merge).get(employee).get("personnel_command_id");
    }

    @Test
    @DisplayName("#910 ADR-0073 守护 1「与会签依据一致的免会签」、note §5 第 4、6 条：已会签合并执行时，每人前后法人与依据一致：逐人生成调岗命令（引用本合并、即时生效、本身不开会签请求），全部计数，合并 EFFECTIVE、被并入单元撤销；继承法人的两人各发一次「员工成本法人已变更」，有自有归属的人不发；只发一次「组织单元已合并」")
    void aConsistentMergeIsExemptAndPublishesOncePerChangedPerson() throws Exception {
        UUID merge = countersignedMerge();
        setToday(DAY);

        orgCommands().executeMerge(merge);

        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("affected_employee_count")).isEqualTo(3);
        assertThat(row.get("transferred_employee_count")).isEqualTo(3);
        Map<UUID, Map<String, Object>> transfers = generatedTransfers(merge);
        assertThat(transfers).containsOnlyKeys(inheritingA, inheritingB, own);
        for (Map<String, Object> transfer : transfers.values()) {
            assertThat(transfer.get("command_kind")).isEqualTo(TRANSFER);
            assertThat(transfer.get("status")).isEqualTo(EFFECTIVE);
            assertThat(rows("select 1 from " + COUNTERSIGNS + " where personnel_command_id = ?", transfer.get("personnel_command_id"))).as("exempt: no own request").isEmpty();
        }
        assertThat(unitOf(inheritingA, DAY)).isEqualTo(receiving);
        assertThat(unitOn(merged, DAY).get("status")).isEqualTo(REVOKED);
        assertThat(legalEntityChangesOf(inheritingA)).isEqualTo(1);
        assertThat(legalEntityChangesOf(inheritingB)).isEqualTo(1);
        assertThat(legalEntityChangesOf(own)).isZero();
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
    }

    @Test
    @DisplayName("#910 note §5 第 2、4 条：待会签的合并不能执行：被拒，仍待会签，没有调岗；生效日到了仍未会签的合并同样不执行")
    void anAwaitingMergeCannotBeExecuted() throws Exception {
        UUID merge = enteredMerge();
        setToday(DAY);

        assertRefused(() -> orgCommands().executeMerge(merge));

        assertThat(statusOf(merge)).isEqualTo(AWAITING);
        assertThat(generatedTransfers(merge)).isEmpty();
        assertThat(unitOf(inheritingA, DAY)).isEqualTo(merged);
    }

    @Test
    @DisplayName("#910 ADR-0073 守护 1 反例「会签后链路变了的人被拦下」、「未完成的合并不显示完成」：会签后承接单元被指定为 E3（承接单元当时没人，不需会签），执行时继承法人的两人之后变成 E3、与依据（E2）不同：他们的调岗各自待法人归属会签（E1、E3 各一行）、不计数；有自有归属的人照常调走并计数；合并停在 IN_PROGRESS（受影响 3、已转 1），被并入单元不撤销，不发「已合并」与「成本法人已变更」")
    void aPersonWhoseChainChangedAfterTheCountersignIsStopped() throws Exception {
        UUID merge = countersignedMerge();
        UUID reassign = assignLegalEntity(receiving, DAY.minusDays(1), e3);
        assertThat(statusOf(reassign)).as("nobody inherits from the receiving unit yet").isIn(PENDING, EFFECTIVE);
        setToday(DAY);

        catchThrowable(() -> orgCommands().executeMerge(merge));

        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(IN_PROGRESS);
        assertThat(row.get("affected_employee_count")).isEqualTo(3);
        assertThat(row.get("transferred_employee_count")).isEqualTo(1);
        Map<UUID, Map<String, Object>> transfers = generatedTransfers(merge);
        assertThat(transfers.get(own).get("status")).isEqualTo(EFFECTIVE);
        for (UUID stopped : List.of(inheritingA, inheritingB)) {
            assertThat(transfers.get(stopped).get("status")).isEqualTo(AWAITING);
            assertThat(rows("select legal_entity_id from " + COUNTERSIGNS + " where personnel_command_id = ? and request_kind = ?",
                    transfers.get(stopped).get("personnel_command_id"), KIND_CHANGE).stream().map(r -> r.get("legal_entity_id")).toList())
                    .containsExactlyInAnyOrder(e1, e3);
            assertThat(unitOf(stopped, DAY)).isEqualTo(merged);
            assertThat(legalEntityChangesOf(stopped)).isZero();
        }
        assertThat(unitOn(merged, DAY).get("status")).isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();
    }

    @Test
    @DisplayName("#910 用户答复 QF1（a，2026-10-30）、note §5 第 4 条：被拦下的人一人会签通过、一人被拒：通过的人调走（成本法人变更一次）；被拒的人的调岗已撤回、仍在被并入单元，合并仍 IN_PROGRESS 不撤销单元；人事用另一条命令在合并日把他调出后，合并才 EFFECTIVE（受影响 = 已转 = 3）、撤销单元、发一次「已合并」")
    void aRejectedPersonKeepsTheMergeOpenUntilMovedOut() throws Exception {
        UUID merge = countersignedMerge();
        assignLegalEntity(receiving, DAY.minusDays(1), e3);
        setToday(DAY);
        catchThrowable(() -> orgCommands().executeMerge(merge));
        UUID transferA = transferOf(merge, inheritingA);
        UUID transferB = transferOf(merge, inheritingB);

        countersignsAs(headE1).approve(personnelRequest(transferB, e1));
        countersignsAs(headE3).approve(personnelRequest(transferB, e3));
        countersignsAs(headE3).reject(personnelRequest(transferA, e3), "synthetic: finance refuses");
        actIn(orgAdmin);
        catchThrowable(() -> orgCommands().executeMerge(merge));

        assertThat(command(transferB).get("status")).isEqualTo(EFFECTIVE);
        assertThat(unitOf(inheritingB, DAY)).isEqualTo(receiving);
        assertThat(legalEntityChangesOf(inheritingB)).isEqualTo(1);
        assertThat(command(transferA).get("status")).as("finance's rejection stands").isEqualTo(WITHDRAWN);
        assertThat(unitOf(inheritingA, DAY)).isEqualTo(merged);
        assertThat(statusOf(merge)).isEqualTo(IN_PROGRESS);
        assertThat(unitOn(merged, DAY).get("status")).isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();

        actInSpecialist();
        UUID movedOut = transfer(inheritingA, DAY, topE1);
        assertThat(command(movedOut).get("status")).isEqualTo(EFFECTIVE);
        actIn(orgAdmin);
        catchThrowable(() -> orgCommands().executeMerge(merge));

        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("affected_employee_count")).isEqualTo(3);
        assertThat(row.get("transferred_employee_count")).isEqualTo(3);
        assertThat(unitOn(merged, DAY).get("status")).isEqualTo(REVOKED);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
        assertThat(legalEntityChangesOf(inheritingA)).as("moved within E1").isZero();
    }

    @Test
    @DisplayName("#910 note §5 第 4 条、ADR-0068 附件「IN_PROGRESS 不可撤回」：被拦下而停在 IN_PROGRESS 的合并不能撤回，也不开撤回请求")
    void aStuckMergeCannotBeWithdrawn() throws Exception {
        UUID merge = countersignedMerge();
        assignLegalEntity(receiving, DAY.minusDays(1), e3);
        setToday(DAY);
        catchThrowable(() -> orgCommands().executeMerge(merge));

        assertRefused(() -> orgCommands().withdraw(merge, "synthetic: give up"));

        assertThat(statusOf(merge)).isEqualTo(IN_PROGRESS);
        assertThat(requests(merge, KIND_WITHDRAWAL)).isEmpty();
    }
}
