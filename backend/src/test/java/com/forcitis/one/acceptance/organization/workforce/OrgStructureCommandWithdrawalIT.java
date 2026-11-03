package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.ACTOR_TYPE_USER;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #906 IMP-1030-ORG-F2a (ADR-0068 item 4 with item 1.6 lines 58 to 60 as the model, attachment trigger tg_org_structure_commands_append_only,
 * W2 section 2): withdrawing a PENDING org-structure command takes a reason of 1 to 500 characters that is not blank, voids the future
 * version the command wrote, frees the day, writes audit and publishes nothing; a command that has taken effect cannot be withdrawn
 * (a mistake is undone by a new command, attachment line 424). Withdrawing an IN_PROGRESS merge is covered in the merge class.
 */
class OrgStructureCommandWithdrawalIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgStructureCommandWithdrawalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 ck_org_structure_commands_withdrawn、触发器 PENDING 到 WITHDRAWN：撤回待生效命令：状态 WITHDRAWN，撤回人是调用者、原因与撤回时刻落库、applied_at 仍空；state_transitions 追加 PENDING 到 WITHDRAWN；写撤回审计、不发事件")
    void withdrawingAPendingCommandRecordsWhoWhenAndWhy() throws Exception {
        UUID target = seedUnit(null);
        UUID command = unitCommand(SET_ATTRIBUTES, target, TODAY.plusDays(8), TARGET_NAME, "Planned name");
        Set<Object> audited = auditIds(AUDIT_WITHDRAWN);

        orgCommands().withdraw(command, "synthetic: wrong name");

        Map<String, Object> row = orgCommand(command);
        assertThat(row.get("status")).isEqualTo(WITHDRAWN);
        assertThat(row.get("withdrawn_by")).isEqualTo(orgAdmin.userId());
        assertThat(row.get("withdrawal_reason")).isEqualTo("synthetic: wrong name");
        assertThat(row.get("withdrawn_at")).isEqualTo(NOW);
        assertThat(row.get("applied_at")).isNull();
        List<Map<String, Object>> steps = transitions(AGGREGATE_ORG_COMMAND, command);
        assertLawfulOrgLifecycle(steps, WITHDRAWN);
        assertThat(steps.getLast().get("from_status")).isEqualTo(PENDING);
        assertThat(steps.getLast().get("actor_id")).isEqualTo(orgAdmin.userId());
        assertThat(steps.getLast().get("actor_type")).isEqualTo(ACTOR_TYPE_USER);
        assertThat(auditIds(AUDIT_WITHDRAWN)).hasSize(audited.size() + 1);
        assertThat(auditEntriesBy(orgAdmin.userId(), AUDIT_WITHDRAWN)).isNotEmpty();
        assertThat(orgEventsOf(command)).as("withdrawal publishes nothing").isZero();
    }

    @Test
    @DisplayName("#906 W2 第 2 节「撤销尚未生效的提前登记 = 把对应的未来版本行作废并置命令 WITHDRAWN」：撤回把该命令写入的未来版本作废而不删除，时间线恢复为录入前的事实，没有未作废的行还引用该命令")
    void withdrawingVoidsTheFutureVersionAndKeepsTheRows() throws Exception {
        UUID target = seedUnit(null);
        LocalDate day = TODAY.plusDays(8);
        UUID command = unitCommand(SET_ATTRIBUTES, target, day, TARGET_NAME, "Planned name");
        assertThat(unitOn(target, day).get("name")).isEqualTo("Planned name");

        orgCommands().withdraw(command, "synthetic: not approved");

        List<Map<String, Object>> current = currentUnitVersions(target);
        assertContiguous(current);
        assertThat(current).noneMatch(row -> command.equals(row.get("change_org_structure_command_id")));
        assertThat(unitOn(target, day).get("name")).isEqualTo(seededName(target));
        assertThat(unitVersions(target).stream().filter(row -> row.get("superseded_at") != null)).as("voided, not deleted").isNotEmpty()
                .allSatisfy(row -> assertThat(row.get("superseding_org_structure_command_id")).isEqualTo(command));
    }

    @Test
    @DisplayName("#906 W2 第 2 节、ADR-0068 附件列说明：撤回尚未生效的新建，其首个版本作废（单元不再有当前事实）；撤回尚未生效的岗位新建同理")
    void withdrawingACreateVoidsItsFirstVersion() throws Exception {
        UUID unitCommand = createUnit(TODAY.plusDays(15));
        UUID positionCommand = createPosition(TODAY.plusDays(15), "WD-" + UUID.randomUUID().toString().substring(0, 8), "Planned position");

        orgCommands().withdraw(unitCommand, "synthetic: not needed");
        orgCommands().withdraw(positionCommand, "synthetic: not needed");

        assertThat(currentUnitVersions(targetUnitOf(unitCommand))).isEmpty();
        assertThat(currentPositionVersions(targetPositionOf(positionCommand))).isEmpty();
        assertThat(orgCommand(unitCommand).get("status")).isEqualTo(WITHDRAWN);
        assertThat(orgCommand(positionCommand).get("status")).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#906 ADR-0073 第 2 条第 3 项、ADR-0068 第 1 条第 5 项（行 54）：撤回后这一天空出来，同一单元可以再录同一天的命令")
    void aWithdrawnCommandFreesItsDay() throws Exception {
        UUID target = seedUnit(null);
        LocalDate day = TODAY.plusDays(8);
        UUID first = unitCommand(SET_ATTRIBUTES, target, day, TARGET_NAME, "First plan");
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, day, TARGET_NAME, "Second plan"));

        orgCommands().withdraw(first, "synthetic: replaced");

        assertThat(unitCommand(SET_ATTRIBUTES, target, day, TARGET_NAME, "Second plan")).isNotNull();
        assertThat(unitOn(target, day).get("name")).isEqualTo("Second plan");
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 ck_org_structure_commands_withdrawal_reason：撤回原因必填、不得空白、1 至 500 字（500 字可）；被拒后命令仍是待生效、版本不变")
    void theReasonIsRequiredAndBounded() throws Exception {
        UUID target = seedUnit(null);
        UUID command = unitCommand(SET_ATTRIBUTES, target, TODAY.plusDays(8), TARGET_NAME, "Planned name");

        assertRefused(() -> orgCommands().withdraw(command, null));
        assertRefused(() -> orgCommands().withdraw(command, ""));
        assertRefused(() -> orgCommands().withdraw(command, "   "));
        assertRefused(() -> orgCommands().withdraw(command, "r".repeat(501)));

        assertThat(orgCommand(command).get("status")).isEqualTo(PENDING);
        assertThat(unitOn(target, TODAY.plusDays(8)).get("name")).isEqualTo("Planned name");
        orgCommands().withdraw(command, "r".repeat(500));
        assertThat(orgCommand(command).get("status")).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件触发器（状态只能前进）与列说明「已生效的以新命令改回」：已生效的命令、已撤回的命令都不能再撤回，状态与版本不变；不存在的命令被拒")
    void onlyAPendingCommandCanBeWithdrawn() throws Exception {
        UUID target = seedUnit(null);
        UUID effective = unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Already in force");
        UUID withdrawn = unitCommand(SET_ATTRIBUTES, target, TODAY.plusDays(8), TARGET_NAME, "Planned name");
        orgCommands().withdraw(withdrawn, "synthetic: first time");
        int audited = auditIds(AUDIT_WITHDRAWN).size();

        assertRefused(() -> orgCommands().withdraw(effective, "synthetic: too late"));
        assertRefused(() -> orgCommands().withdraw(withdrawn, "synthetic: twice"));
        assertRefused(() -> orgCommands().withdraw(UUID.randomUUID(), "synthetic: unknown"));

        assertThat(orgCommand(effective).get("status")).isEqualTo(EFFECTIVE);
        assertThat(orgCommand(effective).get("withdrawn_by")).isNull();
        assertThat(unitOn(target, TODAY).get("name")).isEqualTo("Already in force");
        assertThat(orgCommand(withdrawn).get("withdrawal_reason")).isEqualTo("synthetic: first time");
        assertThat(auditIds(AUDIT_WITHDRAWN)).as("refusals leave no audit").hasSize(audited);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 1 条第 9 项、第 4 条（事件只在命令生效时发布）：每条命令至多一个事件；录入将来生效的不发，同一命令撤回也不发；今天生效的恰一个")
    void eventsOnlyOnEffect() throws Exception {
        UUID target = seedUnit(null);
        UUID head = employedIn(target, PersonnelCommandNames.REGULAR, true);

        UUID future = unitCommand(ASSIGN_HEAD, target, TODAY.plusDays(4), TARGET_HEAD_EMPLOYEE_ID, head);
        UUID now = unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Now");
        orgCommands().withdraw(future, "synthetic: changed mind");

        assertThat(orgEventsOf(future)).isZero();
        assertThat(orgEventsOf(now)).isEqualTo(1);
    }
}
