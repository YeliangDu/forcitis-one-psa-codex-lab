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
 * #943, user decision 2026-10-30 「已录的未来停薪算已走」 (fragment 2026-10-30-161703): an unpaid-leave start that is entered but not yet started, of another
 * super_admin or security_auditor holder, counts as already gone in the unpaid-leave start floor. Left out on purpose (the user did not rule on them): the
 * due-date job re-check, and future leaves counting in the separation, hold-release and role-revoke floors.
 */
class UnpaidLeaveStartFloorFutureIT extends UnpaidLeaveStartFloorSupport {

    static final TestDatabase postgres = TestPostgres.database(UnpaidLeaveStartFloorFutureIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#943 未来日 (1)：A、B 是仅有的可用超管，B 已录未来停薪；再为 A 录未来停薪被拒（422 + 约定码），什么都不写")
    void aFutureStartCountsAsGoneForAFutureStart() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        startAccepted(b, LATER);

        assertRefusedAndNothingWritten(a, LATER);
    }

    @Test
    @DisplayName("#943 未来日 (2)：另有第三名超管 C：A 的未来停薪被接受，剩 1（C）：恰一张卡（PERSONNEL_COMMAND + 该命令 id + UNPAID_LEAVE_START_NOTICE），收件人含 C，不含 A；ALERT 行")
    void aThirdAdministratorMakesTheFutureStartAcceptedWithAnAlert() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        Staff c = administrator();
        startAccepted(b, LATER);
        forgetCards();
        int mark = logMark();

        UUID command = startLeave(a, LATER);

        assertThat(commandStatus(command)).isEqualTo(PENDING);
        WecomCard card = theAlertOf(command);
        assertThat(recipientsOf(card)).contains(wecomUserIdOf(c.employeeId())).doesNotContain(wecomUserIdOf(a.employeeId()));
        assertAlertLine(alertsSince(mark));
    }

    @Test
    @DisplayName("#943 未来日 (3)：A、B 仅有的可用超管，B 已录未来停薪；为 A 录今天生效的停薪也被拒（决定 161703 + 163419：今天与未来同一规则），什么都不写")
    void aFutureStartOfAnotherCountsAsGoneForATodayStart() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        startAccepted(b, LATER);

        assertRefusedAndNothingWritten(a, TODAY);
    }

    @Test
    @DisplayName("#943 未来日 (4)：自己已录的未来停薪不对自己重复扣：A、B 可用，A 已录未来停薪；A 再录今天生效的停薪，只扣 A 一次，剩 B 一名：被接受并发卡，而不是剩 0 被拒")
    void ownFutureStartIsNotCountedTwice() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        startAccepted(a, LATER);
        forgetCards();

        UUID command = startLeave(a, TODAY);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(recipientsOf(theAlertOf(command))).containsExactlyInAnyOrderElementsOf(wecomIdsOf(b));
    }
}
