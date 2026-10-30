package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #931 IMP-1030-PAR-C1 part (b) (ADR-0068 item 2.3, line 91; ADR-0028 decision 4): when exactly one available super administrator remains after
 * a release, the alert reaches the other super administrators, the security auditor holders and operations. Channel and recipients follow the user's
 * ruling of 2026-10-30 on #931 (decision fragment 2026-10-30-142845-one-lead (也发, 只发可用的, 算已走, 不能), PR #940): one WeCom card per
 * release through platform.integration's {@code WecomGateway.send(WecomCard)} with exact recipients (security invariant I-6), operations
 * through the existing {@code ALERT organization.workforce ...} error log line. Recipients are the usable super_admin and security_auditor holders:
 * account enabled, employed today, not in the HELD set, and not the separating person; the releaser is a recipient when
 * they hold either role; a person with both roles is named once. The card subject id equals the hold id (agreed 暂名, a technical detail). The gateway
 * is the outbound double; the release, the roles and the pool are real.
 */
class SeparationHoldAlertRecipientsIT extends FutureHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationHoldAlertRecipientsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final Pattern REMAINING_ONE = Pattern.compile("(?is)(remaining|剩余)\\D{0,40}1(\\D|$)");

    private void disableAccount(Staff who) throws Exception {
        try (Connection identity = schemaConnection("platform_identity")) {
            update(identity, "update user_accounts set status = 'DISABLED' where employee_id = ?", stored(who.employeeId()));
        }
    }

    private WecomCard theOneCard(UUID hold) {
        List<WecomCard> sent = cardsFor(hold);
        assertThat(sent).as("one card for the one release (cards: " + sent + ")").hasSize(1);
        return sent.getFirst();
    }

    @Test
    @DisplayName("#931 ADR-0068 第 2 条第 3 项、ADR-0028 决定 4：放行后剩余 1 名超管：恰好一张企业微信卡，收件人恰为其他超管、全部安全审计员持有人（含放行人，其持有该角色）；不含离职人、不含同批待放行的另一名超管、不含已停用账号的超管与审计员、不含离职已生效但账号仍启用的原超管与原审计员（用户裁定 2026-10-30-142845：可用 = 账号启用且今天在职）、不含无这两种角色的人（人事专员）；卡内标明剩余 1 名；运维仍有 ALERT organization.workforce 日志行")
    void oneRemainingAlertsTheOtherAdministratorsAndAuditors() throws Exception {
        Staff leaver = administrator();
        Staff stays = administrator();
        Staff releaser = auditor();
        Staff anotherAuditor = auditor();
        Staff alsoHeld = administrator();
        Staff disabledAdministrator = administrator();
        Staff disabledAuditor = auditor();
        Staff formerAdministrator = administrator();
        Staff formerAuditor = auditor();
        // Their separations are in effect, their accounts are still enabled (identity is not wired to EmployeeSeparated before C2/C3).
        releaseAs(releaser, heldSeparation(formerAdministrator).hold(), REASON_TEXT);
        releaseAs(releaser, heldSeparation(formerAuditor).hold(), REASON_TEXT);
        assertThat(employmentOn(formerAdministrator.employeeId(), TODAY)).isEqualTo(SEPARATED);
        assertThat(employmentOn(formerAuditor.employeeId(), TODAY)).isEqualTo(SEPARATED);
        Held held = heldSeparation(leaver);
        heldSeparation(alsoHeld);
        disableAccount(disabledAdministrator);
        disableAccount(disabledAuditor);
        forgetCards();
        int mark = logMark();

        releaseAs(releaser, held.hold(), REASON_TEXT);

        WecomCard card = theOneCard(held.hold());
        assertThat(recipientsOf(card)).as("exact recipients").containsExactlyInAnyOrderElementsOf(wecomIdsOf(stays, releaser, anotherAuditor));
        assertThat(recipientsOf(card)).as("not the separating person, the other held administrator, disabled accounts, former holders with an enabled account, or the 人事专员")
                .doesNotContainAnyElementsOf(wecomIdsOf(leaver, alsoHeld, disabledAdministrator, disabledAuditor, formerAdministrator, formerAuditor, hr));
        assertThat(REMAINING_ONE.matcher(card.title() + " " + card.description()).find()).as("the card names the remaining count (1): " + card.title() + " / " + card.description()).isTrue();
        assertThat(card.subject().id()).as("one card per release: the subject is the hold (暂名, doubt)").isEqualTo(held.hold());
        assertThat(alertsSince(mark)).as("operations: the ALERT log line").anySatisfy(line -> assertThat(line).startsWith("ALERT organization.workforce"));
        assertThat(commandStatus(held.command())).as("the release itself went through").isEqualTo(EFFECTIVE);
    }

    @Test
    @DisplayName("#931 附件规则 5（多角色）：同时持有 super_admin 与 security_auditor 的人只在收件人中出现一次；一次放行一张卡，对同一暂停记录再放行被拒后不再发卡")
    void aPersonWithBothRolesIsNamedOnceAndOneReleaseSendsOneCard() throws Exception {
        Staff leaver = administrator();
        Staff both = administrator(SECURITY_AUDITOR);
        Staff releaser = auditor();
        Held held = heldSeparation(leaver);
        forgetCards();

        releaseAs(releaser, held.hold(), REASON_TEXT);

        WecomCard card = theOneCard(held.hold());
        assertThat(recipientsOf(card)).containsExactlyInAnyOrderElementsOf(wecomIdsOf(both, releaser));
        assertThat(recipientsOf(card)).as("once, not once per role").doesNotHaveDuplicates();
        Staff another = auditor();
        assertThatThrownBy(() -> releaseAs(another, held.hold(), "synthetic: again")).isInstanceOf(RuntimeException.class);
        assertThat(cardsFor(held.hold())).as("the refused second release sends nothing").hasSize(1);
    }

    @Test
    @DisplayName("#931 附件规则 5（空范围）：没有任何安全审计员时，放行人为管理模式下唯一留任的超管：收件人恰为这名超管（仅其本人，不含离职人），仍发一张卡")
    void withoutAnySecurityAuditorTheRemainingAdministratorIsTheRecipient() throws Exception {
        Staff leaver = administrator();
        Staff stays = administrator();
        Held held = heldSeparation(leaver);
        Staff inMode = inAdministratorMode(stays);
        forgetCards();

        releaseAs(inMode, held.hold(), REASON_TEXT);

        WecomCard card = theOneCard(held.hold());
        assertThat(recipientsOf(card)).containsExactlyInAnyOrderElementsOf(wecomIdsOf(stays));
    }

    @Test
    @DisplayName("#931 ADR-0068 第 2 条第 3 项（行 90）：放行后剩余 2 名及以上：不发卡，不告警；放行成功")
    void twoRemainingSendNoCard() throws Exception {
        Scenario scenario = heldWithRemaining(1);
        auditor();
        forgetCards();
        int mark = logMark();

        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);

        assertThat(cardsFor(scenario.held().hold())).as("no alert when two remain").isEmpty();
        assertThat(alertsSince(mark)).isEmpty();
        assertThat(commandStatus(scenario.held().command())).isEqualTo(EFFECTIVE);
    }

    @Test
    @DisplayName("#931 ADR-0068 第 2 条第 3 项（行 90）：放行后剩余 0 名：界面拒绝放行，不发卡，暂停记录与命令仍 HELD")
    void noneRemainingIsRefusedAndSendsNothing() throws Exception {
        Staff only = administrator();
        Staff releaser = auditor();
        Held held = heldSeparation(only);
        forgetCards();

        assertThatThrownBy(() -> releaseAs(releaser, held.hold(), REASON_TEXT)).isInstanceOf(RuntimeException.class);

        assertThat(cardsFor(held.hold())).isEmpty();
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        assertThat(commandStatus(held.command())).isEqualTo(HELD);
    }

    @Test
    @DisplayName("#931 ruling #732 + ADR-0028 决定 4：原生效日在将来的放行（剩余 1 名）同样在放行时发这一张卡（命令转 PENDING），到日作业执行离职时不再发第二张卡")
    void aFutureDatedReleaseAlertsAtTheReleaseAndTheJobAddsNoSecondCard() throws Exception {
        Staff leaver = administrator();
        Staff stays = administrator();
        Staff releaser = auditor();
        Held held = heldSeparationOn(leaver, DUE);
        forgetCards();

        releaseAs(releaser, held.hold(), REASON_TEXT);

        assertThat(commandStatus(held.command())).isEqualTo(PENDING);
        WecomCard card = theOneCard(held.hold());
        assertThat(recipientsOf(card)).containsExactlyInAnyOrderElementsOf(wecomIdsOf(stays, releaser));

        setToday(DUE);
        runDueJob();

        assertThat(commandStatus(held.command())).isEqualTo(EFFECTIVE);
        assertThat(cardsFor(held.hold())).as("the alert belongs to the release, not to the job").hasSize(1);
    }
}
