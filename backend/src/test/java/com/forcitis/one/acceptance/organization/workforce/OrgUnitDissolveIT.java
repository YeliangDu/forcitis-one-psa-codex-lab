package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #906 IMP-1030-ORG-F2a (ADR-0068 item 4 and the column notes of its attachment; the third answer of the fourth round: DISSOLVE writes
 * REVOKED): dissolving an org unit is refused while the unit still has employed people, and allowed when it is empty. People who have
 * separated do not count. Whether a unit with child units may be dissolved is not decided in the sources and is not asserted.
 */
class OrgUnitDissolveIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgUnitDissolveIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件列说明「撤销时目标部门仍有在职员工的，应用层拒绝」：有在职员工的单元，撤销（今天生效或将来生效、该员工那天仍在职）被拒，不留命令行、单元版本不变")
    void aUnitWithEmployedPeopleCannotBeDissolved() throws Exception {
        UUID occupied = seedUnit(null);
        employedIn(occupied, REGULAR, true);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(DISSOLVE, occupied, TODAY));
        assertRefused(() -> unitCommand(DISSOLVE, occupied, TODAY.plusDays(5)));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(unitOn(occupied, TODAY).get("status")).isEqualTo(ACTIVE);
        assertThat(currentUnitVersions(occupied)).hasSize(1);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件三问答复第 3 问、列说明：没有在职员工的单元可以撤销：当天生效，写 REVOKED 版本（前一版本在当天关闭），发一次「组织单元已撤销」，状态转换与审计各记一条")
    void anEmptyUnitCanBeDissolved() throws Exception {
        UUID empty = seedUnit(null);

        UUID command = unitCommand(DISSOLVE, empty, TODAY);

        assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
        assertThat(orgCommand(command).get("command_kind")).isEqualTo(DISSOLVE);
        List<Map<String, Object>> current = currentUnitVersions(empty);
        assertContiguous(current);
        assertThat(unitOn(empty, TODAY).get("status")).isEqualTo(REVOKED);
        assertThat(unitOn(empty, TODAY).get("change_org_structure_command_id")).isEqualTo(command);
        assertThat(unitOn(empty, TODAY.minusDays(1)).get("status")).as("the day before it was still active").isEqualTo(ACTIVE);
        assertThat(unitOn(empty, TODAY.minusDays(1)).get("name")).as("the other attributes are carried").isEqualTo(seededName(empty));
        assertLawfulOrgLifecycle(transitions(AGGREGATE_ORG_COMMAND, command), EFFECTIVE);
        assertThat(eventsOf(EVENT_ORG_UNIT_DISSOLVED, command)).isEqualTo(1);
        assertThat(orgEventsOf(command)).isEqualTo(1);
        assertThat(auditEntriesBy(orgAdmin.userId(), AUDIT_ENTERED)).isNotEmpty();
    }

    @Test
    @DisplayName("#906 ADR-0068 附件列说明（以在职名单为准）：离职生效后的人不算在职：同一单元里另有在职员工的那天仍被拒，其离职生效之后（单元已空）可以撤销")
    void separatedPeopleDoNotCount() throws Exception {
        UUID formerlyOccupied = seedUnit(null);
        UUID leaver = employedIn(formerlyOccupied, REGULAR, true);
        UUID stayer = employedIn(formerlyOccupied, REGULAR, true);
        actInSpecialist();
        separate(leaver, TODAY.minusDays(1));
        separate(stayer, TODAY.plusDays(20));
        actIn(orgAdmin);

        assertRefused(() -> unitCommand(DISSOLVE, formerlyOccupied, TODAY));
        assertThat(unitCommand(DISSOLVE, formerlyOccupied, TODAY.plusDays(21))).as("after the second separation the unit is empty").isNotNull();
    }

    @Test
    @DisplayName("#906 ADR-0068 第 1 条第 8 项（行 65 至 66）：将来生效的撤销保持 PENDING，REVOKED 版本已写入；今天读仍有效，生效日起读已撤销；不发事件")
    void aFutureDissolveStaysPending() throws Exception {
        UUID empty = seedUnit(null);
        LocalDate day = TODAY.plusDays(9);

        UUID command = unitCommand(DISSOLVE, empty, day);

        assertThat(orgCommand(command).get("status")).isEqualTo(PENDING);
        assertThat(unitOn(empty, TODAY).get("status")).isEqualTo(ACTIVE);
        assertThat(unitOn(empty, day).get("status")).isEqualTo(REVOKED);
        assertThat(orgEventsOf(command)).isZero();
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 ck_org_structure_commands_contentless_columns：撤销不带名称、上级等内容列；ck_target：目标单元必须存在")
    void aDissolveCarriesNoContentAndNeedsATarget() throws Exception {
        UUID empty = seedUnit(null);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(DISSOLVE, empty, TODAY, TARGET_NAME, "Renamed"));
        assertRefused(() -> unitCommand(DISSOLVE, empty, TODAY, TARGET_PARENT_ORG_UNIT_ID, unit));
        assertRefused(() -> unitCommand(DISSOLVE, UUID.randomUUID(), TODAY));
        assertRefused(() -> unitCommand(DISSOLVE, null, TODAY));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(unitOn(empty, TODAY).get("status")).isEqualTo(ACTIVE);
    }
}
