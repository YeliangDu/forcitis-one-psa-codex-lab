package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #910 acceptance part (#909 F4a), note section 5 rule 5a (ADR-0073 line 72, user answer Q2 a of 2026-10-30): a command awaiting countersign
 * is withdrawn by the drafter directly; a countersigned PENDING org command only through a WITHDRAWAL request set that every head approves;
 * a rejected withdrawal keeps it PENDING; open withdrawal rows cannot be approved once the effective date has come; an org command that
 * never needed countersign is withdrawn by its drafter as before.
 */
class OrgCountersignWithdrawalIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgCountersignWithdrawalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate DAY = TODAY.plusDays(10);

    private UUID unitX;

    private UUID awaiting() throws Exception {
        unitX = seedUnit(topE1);
        inheritingIn(unitX);
        UUID command = assignLegalEntity(unitX, DAY, e2);
        assertThat(statusOf(command)).isEqualTo(AWAITING);
        return command;
    }

    private UUID countersigned() throws Exception {
        UUID command = awaiting();
        approve(headE1, command, KIND_CHANGE, e1);
        approve(headE2, command, KIND_CHANGE, e2);
        assertThat(statusOf(command)).isEqualTo(PENDING);
        return command;
    }

    @Test
    @DisplayName("#910 note §5 第 5a 条：待会签的命令由起草人直接撤回：转已撤回，撤回人与理由落库，两行未决请求都 LAPSED，不写版本；同一天空出可再录")
    void theDrafterWithdrawsAnAwaitingCommandDirectly() throws Exception {
        UUID command = awaiting();

        orgCommands().withdraw(command, "synthetic: wrong unit");

        assertThat(statusOf(command)).isEqualTo(WITHDRAWN);
        assertThat(fromStored(orgCommand(command).get("withdrawn_by"))).isEqualTo(orgAdmin.userId());
        assertThat(orgCommand(command).get("withdrawal_reason")).isEqualTo("synthetic: wrong unit");
        assertThat(decision(command, KIND_CHANGE, e1)).isEqualTo(LAPSED);
        assertThat(decision(command, KIND_CHANGE, e2)).isEqualTo(LAPSED);
        assertThat(unitAttributionRowsWrittenBy(command)).isZero();
        assertThat(assignLegalEntity(unitX, DAY, e3)).as("the day is free again").isNotNull();
    }

    @Test
    @DisplayName("#910 note §5 第 5a 条：撤回已会签的待生效组织命令不立即撤回：仍待生效、版本仍在，按 CHANGE 的法人各开一行 WITHDRAWAL 请求（请求人为撤回的人）；只批一行仍待生效；别的法人负责人与起草人不能批撤回行；两行都批后转已撤回，归属版本作废")
    void withdrawingACountersignedCommandNeedsEveryHead() throws Exception {
        UUID command = countersigned();

        orgCommands().withdraw(command, "synthetic: plan changed");

        assertThat(statusOf(command)).isEqualTo(PENDING);
        assertThat(unitAttributions(unitX)).hasSize(1);
        assertThat(requestedEntities(command, KIND_WITHDRAWAL)).containsExactlyInAnyOrder(e1, e2);
        assertThat(requests(command, KIND_WITHDRAWAL)).allSatisfy(row -> {
            assertThat(fromStored(row.get("requested_by"))).isEqualTo(orgAdmin.userId());
            assertThat(row.get("decision_kind")).isNull();
        });
        assertThat(catchThrowable(() -> approve(headE3, command, KIND_WITHDRAWAL, e1))).as("another entity's head (rv1041x suggestion 3)").isInstanceOf(DomainException.class);
        assertThat(catchThrowable(() -> approve(orgAdmin, command, KIND_WITHDRAWAL, e1))).as("the drafter").isNotNull();
        assertThat(decision(command, KIND_WITHDRAWAL, e1)).isNull();
        approve(headE1, command, KIND_WITHDRAWAL, e1);
        assertThat(statusOf(command)).isEqualTo(PENDING);
        approve(headE2, command, KIND_WITHDRAWAL, e2);

        assertThat(statusOf(command)).isEqualTo(WITHDRAWN);
        assertThat(unitAttributions(unitX)).as("the attribution version is voided, not deleted").isEmpty();
        assertThat(unitAttributionRowsWrittenBy(command)).isEqualTo(1);
    }

    @Test
    @DisplayName("#910 note §5 第 5a 条：撤回请求被拒：命令保持待生效、版本仍在，另一行未决撤回请求 LAPSED；之后可再请求撤回（序号 2）")
    void aRejectedWithdrawalKeepsTheCommand() throws Exception {
        UUID command = countersigned();
        orgCommands().withdraw(command, "synthetic: plan changed");

        reject(headE2, command, KIND_WITHDRAWAL, e2, "synthetic: keep it");

        assertThat(statusOf(command)).isEqualTo(PENDING);
        assertThat(unitAttributions(unitX)).hasSize(1);
        assertThat(decision(command, KIND_WITHDRAWAL, e1)).isEqualTo(LAPSED);
        orgCommands().withdraw(command, "synthetic: asked again");
        assertThat(((Number) request(command, KIND_WITHDRAWAL, e1).get("request_sequence_number")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("#910 note §5 第 5a 条、用户答复 Q2：撤回请求未决时生效日到了，请求不能再批准，命令不转已撤回")
    void anOpenWithdrawalCannotBeApprovedOnTheEffectiveDate() throws Exception {
        UUID command = countersigned();
        orgCommands().withdraw(command, "synthetic: plan changed");
        approve(headE1, command, KIND_WITHDRAWAL, e1);
        setToday(DAY);

        assertThat(catchThrowable(() -> approve(headE2, command, KIND_WITHDRAWAL, e2))).isNotNull();

        assertThat(statusOf(command)).isNotEqualTo(WITHDRAWN);
        assertThat(unitAttributions(unitX)).hasSize(1);
    }

    @Test
    @DisplayName("#910 note §5 第 5a 条：从未需要会签的组织命令由起草人直接撤回（ADR-0068 第 4 条），不开撤回请求；已生效的不能撤回")
    void aCommandWithoutCountersignIsWithdrawnDirectly() throws Exception {
        UUID empty = seedUnit(topE1);
        UUID plain = assignLegalEntity(empty, DAY, e2);
        assertThat(statusOf(plain)).isEqualTo(PENDING);

        orgCommands().withdraw(plain, "synthetic: no longer needed");

        assertThat(statusOf(plain)).isEqualTo(WITHDRAWN);
        assertThat(requests(plain, KIND_WITHDRAWAL)).isEmpty();
        UUID today = assignLegalEntity(seedUnit(topE1), TODAY, e2);
        assertThat(statusOf(today)).isEqualTo(EFFECTIVE);
        assertRefused(() -> orgCommands().withdraw(today, "synthetic: too late"));
    }
}
