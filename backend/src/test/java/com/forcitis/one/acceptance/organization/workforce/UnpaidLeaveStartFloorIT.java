package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #943 (user decision 2026-10-30 「加，同离职一样」; ADR-0068 sections 2.2, 2.3, 3; DD sign-off on the card): a start of unpaid leave that takes effect
 * today for a super_admin or security_auditor holder is refused when no available super administrator would remain (422, nothing written), alerts when
 * exactly one remains and does nothing at two or more. Available = enabled account, employed today, not on unpaid leave, not in a HELD separation, not in a
 * released separation waiting for its date. Ordinary employees are never affected.
 */
class UnpaidLeaveStartFloorIT extends UnpaidLeaveStartFloorSupport {

    static final TestDatabase postgres = TestPostgres.database(UnpaidLeaveStartFloorIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    // ---- 0 remain: refused ----

    @Test
    @DisplayName("#943 剩 0：唯一的可用超管开始停薪留职（今天生效）被拒：422、码 ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED；不写命令行、员工版本、命令审计、投递，不发卡，此人未停薪")
    void theOnlyAdministratorIsRefused() throws Exception {
        Staff only = administrator();
        auditor();

        assertRefusedAndNothingWritten(only, TODAY);
    }

    @Test
    @DisplayName("#943 剩 0 / 多角色：同时持有 super_admin 与 security_auditor 的唯一超管开始停薪被拒，什么都不写")
    void theOnlyAdministratorWithBothRolesIsRefused() throws Exception {
        Staff only = administrator(SECURITY_AUDITOR);

        assertRefusedAndNothingWritten(only, TODAY);
    }

    @Test
    @DisplayName("#943 剩 0 / 边界：另一名超管在待放行（HELD）离职中不算可用：A 开始停薪被拒")
    void aHeldAdministratorIsNotCounted() throws Exception {
        Staff a = administrator();
        Staff held = administrator();
        auditor();
        heldSeparation(held);

        assertRefusedAndNothingWritten(a, TODAY);
    }

    @Test
    @DisplayName("#943 剩 0 / 边界：另一名超管的离职已放行、离职日在将来（待生效）不算可用：A 开始停薪被拒")
    void aReleasedPendingAdministratorIsNotCounted() throws Exception {
        Staff a = administrator();
        Staff leaving = administrator();
        Staff releaser = auditor();
        Held held = heldSeparationOn(leaving, DUE);
        releaseAs(releaser, held.hold(), REASON_TEXT);
        assertThat(commandStatus(held.command())).as("released and waiting for its date").isEqualTo(PENDING);

        assertRefusedAndNothingWritten(a, TODAY);
    }

    @Test
    @DisplayName("#943 剩 0 / 边界：账号已停用的超管不算可用：A 开始停薪被拒")
    void aDisabledAdministratorIsNotCounted() throws Exception {
        Staff a = administrator();
        Staff disabled = administrator();
        disableAccount(disabled);

        assertRefusedAndNothingWritten(a, TODAY);
    }

    @Test
    @DisplayName("#943 剩 0 / 边界：已在停薪留职的超管不算可用：L 先停薪（另有两名可用，被接受），T 之后账号停用，A 再开始停薪被拒")
    void anAdministratorAlreadyOnLeaveIsNotCounted() throws Exception {
        Staff a = administrator();
        Staff onLeave = administrator();
        Staff later = administrator();
        startAccepted(onLeave, TODAY);
        disableAccount(later);
        assertThat(onUnpaidLeaveOn(onLeave.employeeId(), TODAY)).isTrue();

        assertRefusedAndNothingWritten(a, TODAY);
    }

    @Test
    @DisplayName("#943 剩 0 / 多角色：security_auditor 兼 super_admin 的人是超管，另一名超管已停用：他开始停薪被拒")
    void anAuditorWhoIsAlsoTheLastAdministratorIsRefused() throws Exception {
        Staff both = auditor(SUPER_ADMIN);
        Staff disabled = administrator();
        disableAccount(disabled);

        assertRefusedAndNothingWritten(both, TODAY);
    }

    // ---- 1 remains: accepted and alert ----

    @Test
    @DisplayName("#943 剩 1：命令被接受并生效；恰好一张企业微信卡：subject 为 PERSONNEL_COMMAND + 该命令 id + UNPAID_LEAVE_START_ALERT；收件人恰为可用的其他超管与审计员（各一次）；不含开始停薪的人、停用账号的超管与审计员、待放行离职的超管、人事专员；卡内无姓名等个人信息；有 ALERT organization.workforce 日志行")
    void oneRemainingAlertsTheUsableHolders() throws Exception {
        Staff leaving = administrator();
        Staff stays = administrator();
        Staff auditorOne = auditor();
        Staff auditorTwo = auditor();
        Staff disabledAdministrator = administrator();
        Staff disabledAuditor = auditor();
        Staff held = administrator();
        disableAccount(disabledAdministrator);
        disableAccount(disabledAuditor);
        heldSeparation(held);
        forgetCards();
        int mark = logMark();

        UUID command = startLeave(leaving, TODAY);

        assertThat(commandStatus(command)).as("accepted and in effect").isEqualTo(EFFECTIVE);
        assertThat(onUnpaidLeaveOn(leaving.employeeId(), TODAY)).isTrue();
        WecomCard card = theAlertOf(command);
        assertThat(recipientsOf(card)).containsExactlyInAnyOrderElementsOf(wecomIdsOf(stays, auditorOne, auditorTwo));
        assertThat(recipientsOf(card)).doesNotHaveDuplicates();
        assertThat(recipientsOf(card)).doesNotContainAnyElementsOf(wecomIdsOf(leaving, disabledAdministrator, disabledAuditor, held, hr));
        assertNoPersonalData(card, leaving, stays, auditorOne, auditorTwo, held, hr);
        assertAlertLine(alertsSince(mark));
    }

    @Test
    @DisplayName("#943 剩 1 / 多角色：兼任两种角色的剩余超管只在收件人中出现一次")
    void aRecipientWithBothRolesIsNamedOnce() throws Exception {
        Staff leaving = administrator();
        Staff both = administrator(SECURITY_AUDITOR);
        forgetCards();

        UUID command = startLeave(leaving, TODAY);

        WecomCard card = theAlertOf(command);
        assertThat(recipientsOf(card)).containsExactlyInAnyOrderElementsOf(wecomIdsOf(both));
        assertThat(recipientsOf(card)).hasSize(1);
    }

    @Test
    @DisplayName("#943 剩 1 / 边界：另一名超管 L 已在停薪不计入：A、L、第三名；L 先停薪（剩 2，被接受），A 再开始停薪剩 1（第三名）：被接受并发卡，收件人恰为第三名")
    void anAdministratorOnLeaveDoesNotCountButTheThirdOneDoes() throws Exception {
        Staff a = administrator();
        Staff onLeave = administrator();
        Staff third = administrator();
        startAccepted(onLeave, TODAY);
        forgetCards();

        UUID command = startLeave(a, TODAY);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(recipientsOf(theAlertOf(command))).containsExactlyInAnyOrderElementsOf(wecomIdsOf(third));
    }

    @Test
    @DisplayName("#943 剩 1 / 边界：已放行待生效离职的超管算已走：A 开始停薪，剩 1 名（第三名）：接受并发卡，收件人不含已放行待生效的人")
    void aReleasedPendingAdministratorIsNotARecipient() throws Exception {
        Staff a = administrator();
        Staff stays = administrator();
        Staff leaving = administrator();
        Staff releaser = auditor();
        releaseAs(releaser, heldSeparationOn(leaving, DUE).hold(), REASON_TEXT);
        forgetCards();

        UUID command = startLeave(a, TODAY);

        WecomCard card = theAlertOf(command);
        assertThat(recipientsOf(card)).containsExactlyInAnyOrderElementsOf(wecomIdsOf(stays, releaser));
    }

    // ---- security_auditor-only holder: literal reading of the card (the count is of available super administrators after the start) ----

    @Test
    @DisplayName("#943 字面读法：只持 security_auditor 的人开始停薪，可用超管为 0：被拒（422 + 约定码），什么都不写")
    void anAuditorOnlyHolderIsRefusedWhenNoAdministratorIsAvailable() throws Exception {
        Staff onlyAuditor = auditor();
        Staff disabled = administrator();
        disableAccount(disabled);

        assertRefusedAndNothingWritten(onlyAuditor, TODAY);
    }

    @Test
    @DisplayName("#943 字面读法：只持 security_auditor 的人开始停薪，可用超管恰为 1：被接受，一张卡（PERSONNEL_COMMAND + 命令 id + UNPAID_LEAVE_START_ALERT），收件人为该超管与其他审计员、不含本人；ALERT 行")
    void anAuditorOnlyHolderStartingLeaveWithOneAdministratorAlerts() throws Exception {
        Staff onlyAuditor = auditor();
        Staff otherAuditor = auditor();
        Staff admin = administrator();
        forgetCards();
        int mark = logMark();

        UUID command = startLeave(onlyAuditor, TODAY);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        WecomCard card = theAlertOf(command);
        assertThat(recipientsOf(card)).containsExactlyInAnyOrderElementsOf(wecomIdsOf(admin, otherAuditor));
        assertAlertLine(alertsSince(mark));
    }

    // ---- 2 or more remain, ordinary employees: nothing changes ----

    @Test
    @DisplayName("#943 剩 2 及以上：超管开始停薪被接受，不发卡，无 ALERT 行（守住「不变」：在未实现时本例也为绿）")
    void twoRemainingSendNothing() throws Exception {
        Staff leaving = administrator();
        administrator();
        administrator();
        forgetCards();
        int mark = logMark();

        UUID command = startLeave(leaving, TODAY);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(onUnpaidLeaveOn(leaving.employeeId(), TODAY)).isTrue();
        assertNoAlert(command, mark);
    }

    @Test
    @DisplayName("#943 剩 2 及以上 / 仅审计员：只持 security_auditor 的人开始停薪，另有两名可用超管：接受、不发卡、无 ALERT 行（守住「不变」）")
    void anAuditorLeavingWithTwoAdministratorsSendsNothing() throws Exception {
        Staff onlyAuditor = auditor();
        administrator();
        administrator();
        forgetCards();
        int mark = logMark();

        UUID command = startLeave(onlyAuditor, TODAY);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertNoAlert(command, mark);
    }

    @Test
    @DisplayName("#943 普通员工（两种角色都没有）：没有任何超管时开始停薪被接受，不发卡、无 ALERT 行（守住「不变」）")
    void anOrdinaryEmployeeIsUnaffectedWhenNoAdministratorExists() throws Exception {
        UUID ordinary = employed(REGULAR);
        forgetCards();
        int mark = logMark();

        actIn(hr);
        UUID command = enter(ordinary, UNPAID_LEAVE_START, TODAY);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(onUnpaidLeaveOn(ordinary, TODAY)).isTrue();
        assertNoAlert(command, mark);
    }

    @Test
    @DisplayName("#943 普通员工：只有 1 名可用超管时开始停薪被接受，不发卡、无 ALERT 行（守住「不变」）")
    void anOrdinaryEmployeeIsUnaffectedWithOneAdministrator() throws Exception {
        administrator();
        UUID ordinary = employed(REGULAR);
        forgetCards();
        int mark = logMark();

        actIn(hr);
        UUID command = enter(ordinary, UNPAID_LEAVE_START, TODAY);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertNoAlert(command, mark);
    }
}
