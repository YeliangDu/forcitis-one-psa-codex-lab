package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #906 IMP-1030-ORG-F2a (ADR-0068 item 4: reparent, assign head, assign regional receivables manager; attachment columns and checks;
 * ADR-0073 item 2.3 for the one-open-command-per-unit-and-day rule). People in the units keep their individually attributed legal
 * entity, so no cost legal entity changes and none of these commands needs a countersign (F4a covers that). A reparent into the unit's own
 * subtree is refused (one-design ruling 2026-10-30 #906).
 */
class OrgUnitReparentAndHeadsIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgUnitReparentAndHeadsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条、附件 ck_org_structure_commands_reparent_columns：调整上级：新版本的上级是新上级、其余沿用，前一版本关闭，当天生效并发布一次「组织单元上级已调整」；单元里有人也照常（成本法人未变不会签）")
    void reparentingMovesTheUnitUnderANewParent() throws Exception {
        UUID oldParent = seedUnit(null);
        UUID newParent = seedUnit(null);
        UUID target = seedUnit(oldParent);
        employedIn(target, REGULAR, true);

        UUID command = unitCommand(REPARENT, target, TODAY, TARGET_PARENT_ORG_UNIT_ID, newParent);

        assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
        assertContiguous(currentUnitVersions(target));
        assertThat(fromStored(unitOn(target, TODAY).get("parent_org_unit_id"))).isEqualTo(newParent);
        assertThat(unitOn(target, TODAY).get("name")).isEqualTo(seededName(target));
        assertThat(unitOn(target, TODAY).get("change_org_structure_command_id")).isEqualTo(command);
        assertThat(fromStored(unitOn(target, TODAY.minusDays(1)).get("parent_org_unit_id"))).as("history keeps the old parent").isEqualTo(oldParent);
        assertThat(eventsOf(EVENT_REPARENTED, command)).isEqualTo(1);
        assertThat(orgEventsOf(command)).isEqualTo(1);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 ck_org_structure_commands_parent_not_self、_reparent_columns：自己不能做自己的上级；新上级必填且必须存在；调整上级不带名称、负责人等其余内容；被拒不留命令行")
    void aBadReparentIsRefused() throws Exception {
        UUID parent = seedUnit(null);
        UUID target = seedUnit(parent);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(REPARENT, target, TODAY, TARGET_PARENT_ORG_UNIT_ID, target));
        assertRefused(() -> unitCommand(REPARENT, target, TODAY));
        assertRefused(() -> unitCommand(REPARENT, target, TODAY, TARGET_PARENT_ORG_UNIT_ID, UUID.randomUUID()));
        assertRefused(() -> unitCommand(REPARENT, target, TODAY, TARGET_PARENT_ORG_UNIT_ID, unit, TARGET_NAME, "Also renamed"));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(fromStored(unitOn(target, TODAY).get("parent_org_unit_id"))).isEqualTo(parent);
    }

    @Test
    @DisplayName("#906 one-design ruling 2026-10-30 #906（树规则）：调整上级的新上级是本单元自己或生效日在其下的任一单元（子、孙）时被拒（成环）；挂到不在其下的单元可以；不留命令行")
    void aReparentUnderItsOwnSubtreeIsRefused() throws Exception {
        UUID top = seedUnit(null);
        UUID child = seedUnit(top);
        UUID grandchild = seedUnit(child);
        UUID elsewhere = seedUnit(null);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(REPARENT, top, TODAY, TARGET_PARENT_ORG_UNIT_ID, top));
        assertRefused(() -> unitCommand(REPARENT, top, TODAY, TARGET_PARENT_ORG_UNIT_ID, child));
        assertRefused(() -> unitCommand(REPARENT, top, TODAY.plusDays(3), TARGET_PARENT_ORG_UNIT_ID, grandchild));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(unitCommand(REPARENT, top, TODAY, TARGET_PARENT_ORG_UNIT_ID, elsewhere)).isNotNull();
        assertThat(fromStored(unitOn(grandchild, TODAY).get("parent_org_unit_id"))).isEqualTo(child);
    }

    @Test
    @DisplayName("#906 one-design ruling 2026-10-30 #906（按整条时间线校验，rv917x M1 之后的细化）第 1 条：两条不同生效日的调整上级合起来成环时，后录的一条被拒（按今天的树校验或只按生效日校验的实现都不能通过），不留命令行；不成环的照常")
    void twoLaterReparentsThatTogetherFormACycleAreRefused() throws Exception {
        UUID x = seedUnit(null);
        UUID y = seedUnit(null);
        UUID p = seedUnit(null);
        UUID q = seedUnit(null);
        unitCommand(REPARENT, x, TODAY.plusDays(5), TARGET_PARENT_ORG_UNIT_ID, y);
        unitCommand(REPARENT, q, TODAY.plusDays(2), TARGET_PARENT_ORG_UNIT_ID, p);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(REPARENT, y, TODAY.plusDays(2), TARGET_PARENT_ORG_UNIT_ID, x));
        assertRefused(() -> unitCommand(REPARENT, p, TODAY.plusDays(5), TARGET_PARENT_ORG_UNIT_ID, q));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(unitCommand(REPARENT, y, TODAY.plusDays(2), TARGET_PARENT_ORG_UNIT_ID, p)).as("a parent outside the subtree is fine").isNotNull();
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条、附件 ck_org_structure_commands_head_columns：指定负责人：版本的负责人是该员工，其他属性（含区域应收负责人）沿用；发一次「部门负责人已指定」；员工须存在、必填、不带其余内容")
    void assigningAHead() throws Exception {
        UUID target = seedUnit(null);
        UUID head = employedIn(target, REGULAR, true);
        UUID manager = employedIn(target, REGULAR, true);
        unitCommand(ASSIGN_RECEIVABLES_MANAGER, target, TODAY.minusDays(5), TARGET_RECEIVABLES_MANAGER_EMPLOYEE_ID, manager);
        long commands = orgCommandCount();

        UUID command = unitCommand(ASSIGN_HEAD, target, TODAY, TARGET_HEAD_EMPLOYEE_ID, head);

        assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
        assertThat(fromStored(unitOn(target, TODAY).get("head_employee_id"))).isEqualTo(head);
        assertThat(fromStored(unitOn(target, TODAY).get("regional_receivables_manager_id"))).as("the other assignment is kept").isEqualTo(manager);
        assertThat(unitOn(target, TODAY.minusDays(1)).get("head_employee_id")).as("before the day there was no head").isNull();
        assertContiguous(currentUnitVersions(target));
        assertThat(eventsOf(EVENT_HEAD_ASSIGNED, command)).isEqualTo(1);
        assertThat(orgEventsOf(command)).isEqualTo(1);
        assertRefused(() -> unitCommand(ASSIGN_HEAD, target, TODAY.plusDays(1), TARGET_HEAD_EMPLOYEE_ID, UUID.randomUUID()));
        assertRefused(() -> unitCommand(ASSIGN_HEAD, target, TODAY.plusDays(1)));
        assertRefused(() -> unitCommand(ASSIGN_HEAD, target, TODAY.plusDays(1), TARGET_HEAD_EMPLOYEE_ID, head, TARGET_NAME, "Also renamed"));
        assertThat(orgCommandCount()).as("only the manager command and this head command were recorded").isEqualTo(commands + 1);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条、附件 ck_org_structure_commands_receivables_columns：指定区域应收负责人：版本的区域应收负责人是该员工，负责人沿用；发一次「区域应收负责人已指定」；员工须存在、必填")
    void assigningARegionalReceivablesManager() throws Exception {
        UUID target = seedUnit(null);
        UUID head = employedIn(target, REGULAR, true);
        UUID manager = employedIn(target, REGULAR, true);
        unitCommand(ASSIGN_HEAD, target, TODAY.minusDays(5), TARGET_HEAD_EMPLOYEE_ID, head);

        UUID command = unitCommand(ASSIGN_RECEIVABLES_MANAGER, target, TODAY, TARGET_RECEIVABLES_MANAGER_EMPLOYEE_ID, manager);

        assertThat(fromStored(unitOn(target, TODAY).get("regional_receivables_manager_id"))).isEqualTo(manager);
        assertThat(fromStored(unitOn(target, TODAY).get("head_employee_id"))).isEqualTo(head);
        assertThat(eventsOf(EVENT_RECEIVABLES_MANAGER_ASSIGNED, command)).isEqualTo(1);
        assertThat(orgEventsOf(command)).isEqualTo(1);
        assertRefused(() -> unitCommand(ASSIGN_RECEIVABLES_MANAGER, target, TODAY.plusDays(1), TARGET_RECEIVABLES_MANAGER_EMPLOYEE_ID, UUID.randomUUID()));
        assertRefused(() -> unitCommand(ASSIGN_RECEIVABLES_MANAGER, target, TODAY.plusDays(1)));
    }

    @Test
    @DisplayName("#906 ADR-0073 第 2 条第 3 项、ADR-0068 附件 uk_org_structure_commands_open_org_unit_per_day：同一组织单元同一生效日只能有一条未生效命令（任何种类，含调整属性与指定负责人）；换一天、换一个单元、或前一条已生效都不挡")
    void oneOpenCommandPerUnitAndDay() throws Exception {
        UUID target = seedUnit(null);
        UUID other = seedUnit(null);
        UUID head = employedIn(target, REGULAR, true);
        LocalDate day = TODAY.plusDays(6);
        UUID first = unitCommand(ASSIGN_HEAD, target, day, TARGET_HEAD_EMPLOYEE_ID, head);
        assertThat(orgCommand(first).get("status")).isEqualTo(PENDING);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, day, TARGET_NAME, "Same day"));
        assertRefused(() -> unitCommand(ASSIGN_HEAD, target, day, TARGET_HEAD_EMPLOYEE_ID, head));
        assertRefused(() -> unitCommand(DISSOLVE, target, day));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(unitCommand(SET_ATTRIBUTES, target, day.plusDays(1), TARGET_NAME, "Next day")).as("another day").isNotNull();
        assertThat(unitCommand(SET_ATTRIBUTES, other, day, TARGET_NAME, "Another unit")).as("another unit").isNotNull();
        assertThat(unitCommand(SET_ATTRIBUTES, other, TODAY, TARGET_NAME, "First today")).isNotNull();
        assertThat(unitCommand(ASSIGN_HEAD, other, TODAY, TARGET_HEAD_EMPLOYEE_ID, head)).as("an earlier one that already took effect is not open").isNotNull();
    }
}
