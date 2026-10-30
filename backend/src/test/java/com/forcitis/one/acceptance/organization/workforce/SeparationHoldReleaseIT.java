package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #873 IMP-1030-PAR-B4 (ADR-0068 item 2.3, lines 87 to 92; ADR-0028 decision 4): one hold is released by one signed-in releaser. The command
 * then runs at its original effective date (versions written back through the supersede mechanism of W2 section 2), without being re-run
 * and without going to HELD again. After the release 2 available super administrators remain: released; 1: released with an alert;
 * 0: the interface refuses. The audit of the release carries operator, session mode, reason, command id, hold id and the remaining count.
 */
class SeparationHoldReleaseIT extends SeparationHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationHoldReleaseIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private void assertExecutedAtTheOriginalDate(Scenario scenario, int versionsBefore) throws Exception {
        Held held = scenario.held();
        Map<String, Object> hold = hold(held.hold());
        assertThat(hold.get("status")).isEqualTo(RELEASED);
        assertThat(hold.get("release_channel_kind")).isEqualTo(CHANNEL_INTERFACE);
        assertThat(hold.get("released_by")).as("the releaser is the signed-in account").isEqualTo(scenario.releaser().userId());
        assertThat(hold.get("release_reason")).isEqualTo(REASON_TEXT);
        assertThat(hold.get("released_at")).isNotNull();
        Map<String, Object> command = command(held.command());
        assertThat(command.get("status")).isEqualTo(EFFECTIVE);
        assertThat(command.get("applied_at")).isNotNull();
        assertThat(command.get("effective_date")).as("the effective date is still the one that was entered").isEqualTo(TODAY);
        List<Map<String, Object>> current = currentVersions(EMPLOYEE_VERSIONS, held.employee());
        assertContiguous(current);
        Map<String, Object> separated = inForceOn(current, TODAY);
        assertThat(separated.get("employment_status")).isEqualTo(SEPARATED);
        assertThat(separated.get("effective_from_date")).as("written back at the original date, not at the day of the release").isEqualTo(TODAY);
        assertThat(inForceOn(current, TODAY.minusDays(1)).get("employment_status")).isEqualTo(EMPLOYED);
        assertThat(allVersions(EMPLOYEE_VERSIONS, held.employee())).as("history is superseded, never deleted").hasSizeGreaterThanOrEqualTo(versionsBefore);
        assertContiguous(currentVersions(ASSIGNMENT_VERSIONS, held.employee()));
        assertThat(commandCount(held.employee())).as("the command is not entered again").isEqualTo(1);
        assertThat(rows("select 1 from " + SEPARATION_HOLDS + " where employee_id = ?", stored(held.employee()))).as("and does not go to HELD again").hasSize(1);
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command())).endsWith(HELD, EFFECTIVE).containsOnlyOnce(HELD);
        assertThat(statusPath(AGGREGATE_SEPARATION_HOLD, held.hold())).endsWith(HELD, RELEASED);
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isEqualTo(1);
        assertThat(eventsOf(EVENT_HELD, held.command())).isEqualTo(1);
        List<String> released = eventJsonOf(EVENT_HOLD_RELEASED, held.command());
        assertThat(released).hasSize(1);
        assertThat(released.getFirst()).containsPattern("\"effectiveDate\"\\s*:\\s*\"" + TODAY + "\"").contains(held.hold().toString());
        assertThat(profileStatus(scenario.leaver())).as("the session of the leaver is revoked when the separation runs").isIn(401, 403);
        assertThat(employmentOn(scenario.stays().employeeId(), TODAY)).isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 88 至 90）、第 1 条第 8 项（行 67）：放行后剩余 2 名超管：放行成功不告警；离职按原生效日经版本补写（放行发生在三天后，离职版本仍从原生效日起，前后版本衔接），不重跑、不再待放行，只发一次 EmployeeSeparated 与一次 SeparationHoldReleased（带原生效日）")
    void releaseWithTwoRemainingExecutesAtTheOriginalDate() throws Exception {
        Scenario scenario = heldWithRemaining(1);
        assertThat(profileStatus(scenario.leaver())).as("held, the person is still signed in").isEqualTo(200);
        int versionsBefore = allVersions(EMPLOYEE_VERSIONS, scenario.held().employee()).size();
        setToday(TODAY.plusDays(3));
        Staff releaser = new Staff(scenario.releaser().employeeId(), signIn(scenario.releaser().employeeId()));
        int mark = logMark();

        releaseAs(releaser, scenario.held().hold(), REASON_TEXT);

        assertThat(alertsSince(mark)).as("two remain: no alert").isEmpty();
        assertExecutedAtTheOriginalDate(new Scenario(scenario.held(), scenario.leaver(), releaser, scenario.stays()), versionsBefore);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 90）、ADR-0028 决定 4：放行后剩余 1 名超管：允许放行并告警；此时剩余少于 2 名，但放行不重新判定、不再进入待放行，离职照常生效")
    void releaseWithOneRemainingIsAllowedWithAnAlertAndIsNotHeldAgain() throws Exception {
        Scenario scenario = heldWithRemaining(0);
        int versionsBefore = allVersions(EMPLOYEE_VERSIONS, scenario.held().employee()).size();
        int mark = logMark();

        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);

        assertThat(alertsSince(mark)).as("one remains: an alert").isNotEmpty();
        assertExecutedAtTheOriginalDate(scenario, versionsBefore);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 90）、附件守护规则 2：放行后剩余 0 名超管（唯一的超管被暂停）：界面拒绝，持有放行权限的安全审计员也不行；暂停记录与命令仍为 HELD，此人在职，不写放行审计、不发事件")
    void releaseWithNoneRemainingIsRefusedOnTheInterface() throws Exception {
        Staff only = administrator();
        Staff releaser = auditor();
        Held held = heldSeparation(only);
        int audited = auditCount(AUDIT_HOLD_RELEASED);

        Throwable refused = org.assertj.core.api.Assertions.catchThrowable(() -> releaseAs(releaser, held.hold(), REASON_TEXT));

        assertThat(refused).as("releasing the last administrator").isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).as("the releaser holds the permission; the refusal is about the count").isFalse();
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        assertThat(commandStatus(held.command())).isEqualTo(HELD);
        assertThat(employmentOn(only.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(hold(held.hold()).get("released_at")).isNull();
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isEqualTo(audited);
        assertThat(eventsOf(EVENT_HOLD_RELEASED, held.command())).isZero();
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isZero();
        assertThat(profileStatus(only)).isEqualTo(200);
    }

    private static final Pattern REMAINING_ONE = Pattern.compile("(?is)(remaining|剩余)\\D{0,40}1(\\D|$)");

    private void assertReleaseAudit(Scenario scenario, Staff releaser, String modeToken) throws Exception {
        List<Map<String, Object>> entries = releaseAuditBy(releaser);
        assertThat(entries).as("one audit entry of the release by this operator").hasSize(1);
        Map<String, Object> entry = entries.getFirst();
        String detail = String.valueOf(entry.get("detail"));
        assertThat(entry.get("action")).isEqualTo(AUDIT_HOLD_RELEASED);
        assertThat(entry.get("actorId")).as("operator").isEqualTo(releaser.userId().toString());
        assertThat(String.valueOf(entry.get("subjectKind"))).isEqualTo("EMPLOYEE");
        assertThat(String.valueOf(entry.get("subjectId"))).isEqualTo(scenario.held().employee().toString());
        assertThat(detail).as("reason").contains(REASON_TEXT);
        assertThat(detail).as("session mode field in: " + detail).containsPattern(SeparationHoldSupport.sessionModePattern(modeToken));
        assertThat(detail).as("and not the other mode").doesNotContainPattern(SeparationHoldSupport.sessionModePattern(modeToken.equals("ORDINARY") ? "ADMINISTRATOR" : "ORDINARY"));
        assertThat(detail).as("command id").contains(scenario.held().command().toString());
        assertThat(detail).as("hold id").contains(scenario.held().hold().toString());
        assertThat(REMAINING_ONE.matcher(detail).find()).as("remaining available super administrators (1) in: " + detail).isTrue();
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 91）：安全审计员在普通会话放行：审计 organization.workforce.separation-hold.released，操作人为放行账号、主体为被放行员工（EMPLOYEE），内容含会话模式 ORDINARY、原因、命令 ID、暂停记录 ID 与剩余可用超管数 1")
    void theReleaseAuditCarriesTheFactsInAnOrdinarySession() throws Exception {
        Scenario scenario = heldWithRemaining(0);

        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);

        assertReleaseAudit(scenario, scenario.releaser(), "ORDINARY");
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 91）、ADR-0028 决定 3：超管进入管理模式后放行（管理模式持有放行权限）：审计的会话模式为 ADMINISTRATOR，放行人记为该超管账号")
    void theReleaseAuditNamesTheAdministratorMode() throws Exception {
        Scenario scenario = heldWithRemaining(0);
        Staff inMode = inAdministratorMode(scenario.stays());

        releaseAs(inMode, scenario.held().hold(), REASON_TEXT);

        assertThat(hold(scenario.held().hold()).get("released_by")).isEqualTo(inMode.userId());
        assertReleaseAudit(scenario, inMode, "ADMINISTRATOR");
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 1 项（行 79）、附件守护规则 2「放行只能一次」：已放行的暂停记录再放行被拒：不再执行、不再发事件、不再写审计，放行人与放行时间不变")
    void aHoldIsReleasedOnlyOnce() throws Exception {
        int audited = auditCount(AUDIT_HOLD_RELEASED);
        Scenario scenario = heldWithRemaining(1);
        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);
        Map<String, Object> first = hold(scenario.held().hold());
        Staff another = auditor();

        assertThatThrownBy(() -> releaseAs(another, scenario.held().hold(), "synthetic: again")).isInstanceOf(RuntimeException.class);

        assertThat(hold(scenario.held().hold())).as("nothing about the first release changes").isEqualTo(first);
        assertThat(eventsOf(EVENT_SEPARATED, scenario.held().command())).isEqualTo(1);
        assertThat(eventsOf(EVENT_HOLD_RELEASED, scenario.held().command())).isEqualTo(1);
        assertThat(releaseAuditBy(scenario.releaser())).hasSize(1);
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).as("one entry for the one release, none for the refused second").isEqualTo(audited + 1);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 87 至 89）边界：不存在的暂停记录 ID 放行被拒（不是权限拒绝），不写审计、不发事件")
    void anUnknownHoldCannotBeReleased() throws Exception {
        Staff releaser = auditor();
        int audited = auditCount(AUDIT_HOLD_RELEASED);

        Throwable refused = org.assertj.core.api.Assertions.catchThrowable(() -> releaseAs(releaser, UUID.randomUUID(), REASON_TEXT));

        assertThat(refused).isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).isFalse();
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isEqualTo(audited);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 89）：放行后再录的他人命令不受影响，被放行员工的下一条命令（复职）按离职已生效处理——复职要求对应的离职已生效，这条证明放行真的把离职执行了")
    void afterTheReleaseTheSeparationIsFactAndTheEmployeeCanBeReinstated() throws Exception {
        Scenario scenario = heldWithRemaining(1);
        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);
        actIn(hr);
        LocalDate day = TODAY.plusDays(5);

        UUID reinstated = reinstate(scenario.held().employee(), day, newUnit(), newPosition());

        assertThat(commandStatus(reinstated)).isIn(PENDING, EFFECTIVE);
        assertThat(employmentOn(scenario.held().employee(), day.plusDays(1))).isEqualTo(EMPLOYED);
    }
}
