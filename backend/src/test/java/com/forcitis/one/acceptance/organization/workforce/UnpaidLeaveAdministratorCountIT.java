package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #873 IMP-1030-PAR-B4 (ADR-0068 section 3, lines 104 to 110): a person on unpaid leave counts neither as retained nor as an available super
 * administrator. The administrator separation review (the fifth of the five in-service checks, line 110) is driven through the hold path:
 * the leave is entered as UNPAID_LEAVE_START / UNPAID_LEAVE_END commands, the count is read from what the hold path does.
 */
class UnpaidLeaveAdministratorCountIT extends SeparationHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(UnpaidLeaveAdministratorCountIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private UUID leave(String kind, Staff who) throws Exception {
        actIn(hr);
        UUID command = enter(who.employeeId(), kind, kind.equals(UNPAID_LEAVE_START) ? TODAY.minusDays(3) : TODAY.minusDays(1));
        assertThat(commandStatus(command)).as(kind + " takes effect on entry and is never held").isEqualTo(EFFECTIVE);
        return command;
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 107、110）、第 2 条第 2 项（行 83）：停薪留职的超管不算可用超管：三名超管中 S2 停薪留职，S1 离职剩下可用的只有 S3 一名，被暂停；停薪留职命令本身直接生效、此人账号与超管授予保留")
    void anAdministratorOnLeaveIsNotAvailableWhenAnotherIsSeparated() throws Exception {
        Staff s1 = administrator();
        Staff s2 = administrator();
        Staff s3 = administrator();
        leave(UNPAID_LEAVE_START, s2);
        assertThat(onUnpaidLeaveOn(s2.employeeId(), TODAY)).isTrue();
        assertThat(employmentOn(s2.employeeId(), TODAY)).as("on leave is still employed").isEqualTo(EMPLOYED);

        UUID command = separateAsHr(s1);

        assertThat(commandStatus(command)).as("only S3 is available, fewer than two").isEqualTo(HELD);
        assertThat(employmentOn(s1.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(employmentOn(s3.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(profileStatus(s2)).as("the leave keeps the session").isEqualTo(200);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 107）、第 2 条第 3 项（行 90）：放行时停薪留职的超管不计入剩余可用超管：S3 停薪留职后放行 S2 的暂停剩余 0 名，界面拒绝；S3 结束停薪留职后剩余 1 名，放行成功并告警")
    void anAdministratorOnLeaveIsNotCountedAtRelease() throws Exception {
        Scenario scenario = heldWithRemaining(0);
        leave(UNPAID_LEAVE_START, scenario.stays());

        Throwable refused = catchThrowable(() -> releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT));

        assertThat(refused).as("nobody available would remain").isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).isFalse();
        assertThat(holdStatus(scenario.held().hold())).isEqualTo(HELD);
        assertThat(employmentOn(scenario.leaver().employeeId(), TODAY)).isEqualTo(EMPLOYED);

        leave(UNPAID_LEAVE_END, scenario.stays());
        assertThat(onUnpaidLeaveOn(scenario.stays().employeeId(), TODAY)).isFalse();
        int mark = logMark();

        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);

        assertThat(holdStatus(scenario.held().hold())).isEqualTo(RELEASED);
        assertThat(employmentOn(scenario.leaver().employeeId(), TODAY)).isEqualTo(SEPARATED);
        assertThat(alertsSince(mark)).as("one remains again: an alert").isNotEmpty();
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 107）、第 2 条第 2 项（行 84）：停薪留职的人不算留任：持有放行权限的审计员停薪留职期间放行被拒（不是权限拒绝），结束后可以放行")
    void aReleaserOnLeaveIsNotRetained() throws Exception {
        Scenario scenario = heldWithRemaining(1);
        leave(UNPAID_LEAVE_START, scenario.releaser());

        Throwable refused = catchThrowable(() -> releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT));

        assertThat(refused).isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).as("the permission is held; the person is not retained: " + refused).isFalse();
        assertThat(holdStatus(scenario.held().hold())).isEqualTo(HELD);
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isZero();

        leave(UNPAID_LEAVE_END, scenario.releaser());

        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);

        assertThat(holdStatus(scenario.held().hold())).isEqualTo(RELEASED);
        assertThat(hold(scenario.held().hold()).get("released_by")).isEqualTo(scenario.releaser().userId());
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 107）边界：停薪留职结束后超管重新计入可用超管：三名超管中 S2 先停薪留职再结束，S1 离职剩下两名，不暂停")
    void anAdministratorBackFromLeaveIsAvailableAgain() throws Exception {
        Staff s1 = administrator();
        Staff s2 = administrator();
        administrator();
        leave(UNPAID_LEAVE_START, s2);
        leave(UNPAID_LEAVE_END, s2);

        UUID command = separateAsHr(s1);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(employmentOn(s1.employeeId(), TODAY)).isEqualTo(SEPARATED);
    }
}
