package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1146 acceptance part (due job of org-structure commands; ADR-0068 items 1.8 and 15.2: the job only flips a due PENDING command to EFFECTIVE,
 * writes applied_at and publishes the events; business judgement and reads never depend on it; #910 ruling point 3; note
 * org-structure-commands.md section 5 rules 4 and 6). Each command is entered with a future effective date, so it is PENDING until the job
 * runs at its day.
 */
class OrgStructureCommandDueJobIT extends OrgStructureCommandDueSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgStructureCommandDueJobIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#1146 完成标准 1：将来生效日的已会签 ASSIGN_LEGAL_ENTITY 在生效日由作业转 EFFECTIVE 并写 applied_at，依据里的每位员工各发一次「员工成本法人已变更」，依据外的人不发；生效日前跑作业不动它")
    void aCountersignedAssignmentTakesEffectOnItsDayWithOneEventPerBasisEmployee() throws Exception {
        Countersigned signed = countersignedAssignment(DAY);
        UUID bystander = inheritingIn(topE1);
        assertThat(statusOf(signed.command())).isEqualTo(PENDING);

        runDueJobOn(DAY.minusDays(1));

        assertThat(statusOf(signed.command())).as("the day before").isEqualTo(PENDING);
        assertThat(appliedAt(signed.command())).isNull();
        assertThat(legalEntityChangesOf(signed.first())).isZero();

        runDueJobOn(DAY);

        assertThat(statusOf(signed.command())).isEqualTo(EFFECTIVE);
        assertThat(appliedAt(signed.command())).isNotNull();
        assertThat(legalEntityChangesOf(signed.first())).isEqualTo(1);
        assertThat(legalEntityChangesOf(signed.second())).isEqualTo(1);
        assertThat(legalEntityChangesOf(bystander)).isZero();
        assertThat(unitAttributions(signed.unit())).singleElement().satisfies(row -> assertThat(row.get("legal_entity_id")).isEqualTo(e2));
    }

    @Test
    @DisplayName("#1146 完成标准 1：同一天再跑一次、隔天再跑，已生效命令的事件不再发（每位员工仍是一次），状态与 applied_at 不变")
    void aSecondRunSendsNothingMore() throws Exception {
        Countersigned signed = countersignedAssignment(DAY);
        runDueJobOn(DAY);
        Object appliedFirst = appliedAt(signed.command());

        runDueJob();
        runDueJobOn(DAY.plusDays(1));

        assertThat(statusOf(signed.command())).isEqualTo(EFFECTIVE);
        assertThat(appliedAt(signed.command())).isEqualTo(appliedFirst);
        assertThat(legalEntityChangesOf(signed.first())).isEqualTo(1);
        assertThat(legalEntityChangesOf(signed.second())).isEqualTo(1);
    }

    @Test
    @DisplayName("#1146 完成标准 1：未经会签的将来生效日 REPARENT、SET_ATTRIBUTES 同样在生效日转 EFFECTIVE，各发一次对应的组织事件（OrgUnitReparented、OrgUnitAttributesSet），生效日前不发，再跑不重发")
    void plainFutureDatedReparentAndSetAttributesTakeEffectWithTheirOrgEvents() throws Exception {
        UUID unit = seedUnit(topE1);
        UUID newParent = seedUnit(topE1);
        UUID reparent = reparent(unit, DAY, newParent);
        UUID rename = unitCommand(SET_ATTRIBUTES, newParent, DAY, TARGET_NAME, "Synthetic renamed by the due job");
        assertThat(statusOf(reparent)).isEqualTo(PENDING);
        assertThat(statusOf(rename)).isEqualTo(PENDING);

        runDueJobOn(DAY.minusDays(1));

        assertThat(eventsOf(EVENT_REPARENTED, reparent)).isZero();
        assertThat(eventsOf(EVENT_ATTRIBUTES_SET, rename)).isZero();

        runDueJobOn(DAY);
        runDueJob();

        assertThat(statusOf(reparent)).isEqualTo(EFFECTIVE);
        assertThat(statusOf(rename)).isEqualTo(EFFECTIVE);
        assertThat(appliedAt(reparent)).isNotNull();
        assertThat(eventsOf(EVENT_REPARENTED, reparent)).isEqualTo(1);
        assertThat(eventsOf(EVENT_ATTRIBUTES_SET, rename)).isEqualTo(1);
        assertThat(orgEventsOf(reparent)).as("no other org event for the reparent").isEqualTo(1);
    }

    @Test
    @DisplayName("#1146 完成标准 2、ADR-0068 第 1 条第 8 项：作业停摆时读取仍然正确、只延后事件：生效日过后三天没跑作业，那天的单元版本已按新上级生效、命令仍 PENDING、没有事件；作业补跑后转 EFFECTIVE，事件恰好一次")
    void anOutageLeavesReadsRightAndOnlyDelaysTheEvents() throws Exception {
        UUID unit = seedUnit(topE1);
        UUID newParent = seedUnit(topE1);
        UUID reparent = reparent(unit, DAY, newParent);
        setToday(DAY.plusDays(3));

        assertThat(unitOn(unit, DAY.minusDays(1)).get("parent_org_unit_id")).as("before the day").isEqualTo(topE1);
        assertThat(unitOn(unit, DAY).get("parent_org_unit_id")).as("on the day, no job has run").isEqualTo(newParent);
        assertThat(statusOf(reparent)).isEqualTo(PENDING);
        assertThat(eventsOf(EVENT_REPARENTED, reparent)).isZero();

        runDueJob();

        assertThat(statusOf(reparent)).isEqualTo(EFFECTIVE);
        assertThat(eventsOf(EVENT_REPARENTED, reparent)).isEqualTo(1);
        assertThat(unitOn(unit, DAY).get("parent_org_unit_id")).isEqualTo(newParent);
    }

    @Test
    @DisplayName("#1146 目标「过点未执行即告警」：生效日当天跑作业不告警；生效日已过两天才跑（错过一次）记至少一条以 ALERT 开头的 ERROR 日志，且命令仍被补跑成 EFFECTIVE")
    void aMissedRunIsAlertedAndCaughtUp() throws Exception {
        UUID onTime = reparent(seedUnit(topE1), DAY, seedUnit(topE1));
        runDueJobOn(DAY);
        assertThat(statusOf(onTime)).isEqualTo(EFFECTIVE);
        assertThat(alerts()).as("a run on the day is not a missed run").isEmpty();

        UUID late = reparent(seedUnit(topE1), DAY.plusDays(5), seedUnit(topE1));
        runDueJobOn(DAY.plusDays(7));

        assertThat(statusOf(late)).isEqualTo(EFFECTIVE);
        assertThat(alerts()).as("a command two days past its day was found PENDING").isNotEmpty().allSatisfy(line -> assertThat(line).contains("organization.workforce"));
    }

    @Test
    @DisplayName("#1146 不得做／完成标准 1：作业只动到日的 PENDING：已撤回的命令、还在待会签的命令（没有版本）、生效日在后的命令，作业日都不变；待会签命令不发事件")
    void onlyDuePendingCommandsAreTouched() throws Exception {
        UUID withdrawn = reparent(seedUnit(topE1), DAY, seedUnit(topE1));
        orgCommands().withdraw(withdrawn, "synthetic: not wanted");
        UUID unitAwaiting = seedUnit(topE1);
        UUID person = inheritingIn(unitAwaiting);
        UUID awaiting = assignLegalEntity(unitAwaiting, DAY, e2);
        UUID later = reparent(seedUnit(topE1), DAY.plusDays(3), seedUnit(topE1));
        assertThat(statusOf(awaiting)).isEqualTo(AWAITING);

        runDueJobOn(DAY);

        assertThat(statusOf(withdrawn)).isEqualTo(WITHDRAWN);
        assertThat(statusOf(awaiting)).isEqualTo(AWAITING);
        assertThat(unitAttributionRowsWrittenBy(awaiting)).isZero();
        assertThat(legalEntityChangesOf(person)).isZero();
        assertThat(statusOf(later)).isEqualTo(PENDING);
        assertThat(eventsOf(EVENT_REPARENTED, withdrawn)).isZero();
    }

    @Test
    @DisplayName("#1146 目标、note §5 第 4 条：到日的 MERGE 由作业从 PENDING 起步（IN_PROGRESS，若人都已转完则 EFFECTIVE）：被并入单元里的人转到承接单元恰好一条生成的调岗，再跑不再生成；不会停在 PENDING")
    void aDueMergeIsStartedByTheJobOnce() throws Exception {
        UUID merged = seedUnit(topE1);
        UUID receiving = seedUnit(topE1);
        UUID person = inheritingIn(merged);
        UUID merge = merge(merged, receiving, DAY);
        assertThat(statusOf(merge)).isEqualTo(PENDING);

        runDueJobOn(DAY.minusDays(1));
        assertThat(statusOf(merge)).as("the day before").isEqualTo(PENDING);

        runDueJobOn(DAY);

        assertThat(statusOf(merge)).isIn(IN_PROGRESS, EFFECTIVE);
        assertThat(generatedTransfers(merge)).containsOnlyKeys(person);
        assertThat(unitOf(person, DAY)).isEqualTo(receiving);
        assertThat(legalEntityChangesOf(person)).as("both units sit under E1: no change of cost legal entity").isZero();

        runDueJob();

        assertThat(generatedTransfers(merge)).as("a second run generates nothing more").containsOnlyKeys(person);
    }

    @Test
    @DisplayName("#1146 目标「每条命令一个事务」：一条命令在提交时失败（约束触发器），它前后各一条到日命令照常生效；失败的那条仍 PENDING、没有事件；故障去掉后下一次作业才生效并恰好发一次事件")
    void oneCommandFailingDoesNotStopTheOthersAndIsRetried() throws Exception {
        UUID healthyBefore = reparent(seedUnit(topE1), DAY.minusDays(1), seedUnit(topE1));
        UUID failing = reparent(seedUnit(topE1), DAY, seedUnit(topE1));
        UUID healthy = reparent(seedUnit(topE1), DAY.plusDays(1), seedUnit(topE1));
        failCommitOfEffective(failing);
        try {
            Throwable failure = catchThrowable(() -> runDueJobOn(DAY.plusDays(1)));

            assertThat(statusOf(healthyBefore)).as("the command before it (job failure: " + failure + ")").isEqualTo(EFFECTIVE);
            assertThat(statusOf(healthy)).as("the command after it").isEqualTo(EFFECTIVE);
            assertThat(eventsOf(EVENT_REPARENTED, healthy)).isEqualTo(1);
            assertThat(statusOf(failing)).isEqualTo(PENDING);
            assertThat(eventsOf(EVENT_REPARENTED, failing)).isZero();
        } finally {
            removeFailure();
        }

        runDueJob();

        assertThat(statusOf(failing)).isEqualTo(EFFECTIVE);
        assertThat(eventsOf(EVENT_REPARENTED, failing)).isEqualTo(1);
        assertThat(eventsOf(EVENT_REPARENTED, healthy)).as("the healthy one is not sent again").isEqualTo(1);
        assertThat(eventsOf(EVENT_REPARENTED, healthyBefore)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1146 完成标准 1：同一天到日的两条命令（一条带员工的会签指派、一条改名）一次作业都生效，各自的事件各一次，互不挡住")
    void twoCommandsDueOnTheSameDayBothTakeEffect() throws Exception {
        Countersigned signed = countersignedAssignment(DAY);
        UUID rename = unitCommand(SET_ATTRIBUTES, seedUnit(topE1), DAY, TARGET_NAME, "Synthetic same day rename");

        runDueJobOn(DAY);

        assertThat(statusOf(signed.command())).isEqualTo(EFFECTIVE);
        assertThat(statusOf(rename)).isEqualTo(EFFECTIVE);
        assertThat(eventsOf(EVENT_ATTRIBUTES_SET, rename)).isEqualTo(1);
        assertThat(legalEntityChangesOf(signed.first())).isEqualTo(1);
    }
}
