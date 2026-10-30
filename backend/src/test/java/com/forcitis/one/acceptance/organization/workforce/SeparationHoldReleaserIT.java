package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #873 IMP-1030-PAR-B4 (ADR-0068 item 2.2 line 84 and item 2.3 line 88; ADR-0028 decisions 3 and 4; ADR-0009; ADR-0055 decision 3 item 4): who
 * may release. The releaser holds {@code organization.workforce:separation-hold:approve} (security auditor in an ordinary session,
 * super administrator in the administrator mode only), is employed and is not in the set of held people, themselves included. The reason
 * is 1 to 500 characters and not blank. The permission is the only gate that a role can give, and the personnel specialist's permission
 * to enter commands does not open the release, nor the other way round.
 */
class SeparationHoldReleaserIT extends SeparationHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationHoldReleaserIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** S2 held, S3 stays: a hold whose release would leave one administrator, so the count is no reason to refuse. */
    private Held oneHold() throws Exception {
        Staff s2 = administrator();
        administrator();
        return heldSeparation(s2);
    }

    private void assertStillHeld(Held held) throws Exception {
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        assertThat(commandStatus(held.command())).isEqualTo(HELD);
        assertThat(employmentOn(held.employee(), TODAY)).isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 84）、ADR-0028 决定 4：放行人本人也在待放行中时不算留任：S2 与 S3 先后被暂停，S3（同持安全审计员角色，有权限）放行 S2 的暂停被拒；另一名不在集合中的审计员可以放行 S2 的暂停")
    void aReleaserWhoIsHeldHimselfIsNotRetained() throws Exception {
        Staff s2 = administrator();
        Staff s3 = administrator(SECURITY_AUDITOR);
        Staff outsider = auditor();
        Held second = heldSeparation(s2);
        Held third = heldSeparation(s3);
        administrator();

        Throwable refused = catchThrowable(() -> releaseAs(s3, second.hold(), REASON_TEXT));

        assertThat(refused).isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).as("S3 holds the permission through the security auditor role; he is refused as a held person").isFalse();
        assertStillHeld(second);
        assertStillHeld(third);
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isZero();

        releaseAs(outsider, second.hold(), REASON_TEXT);

        assertThat(holdStatus(second.hold())).isEqualTo(RELEASED);
        assertThat(employmentOn(s2.employeeId(), TODAY)).isEqualTo(SEPARATED);
        assertStillHeld(third);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 84）、附件守护规则 2「放行人自己在待放行中时不能放行」：持有权限的人放行自己被暂停的离职被拒，暂停记录与命令仍为 HELD，此人仍在职、不写放行审计")
    void aReleaserCannotReleaseTheirOwnHold() throws Exception {
        Staff leaver = administrator(SECURITY_AUDITOR);
        administrator();
        administrator();
        Held own = heldSeparation(leaver);
        int audited = auditCount(AUDIT_HOLD_RELEASED);

        Throwable refused = catchThrowable(() -> releaseAs(leaver, own.hold(), REASON_TEXT));

        assertThat(refused).isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).isFalse();
        assertStillHeld(own);
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isEqualTo(audited);
        assertThat(eventsOf(EVENT_HOLD_RELEASED, own.command())).isZero();
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 88）、ADR-0009、ADR-0028 决定 3（提权）：没有 separation-hold:approve 的人放行一律被拒（权限拒绝）：人事专员（有录入权限）、组织数据管理员、只有默认角色的员工、普通会话里的超管、未登录；每次拒绝后暂停记录仍为 HELD，无放行审计")
    void everyoneWithoutThePermissionIsRefused() throws Exception {
        Held held = oneHold();
        List<Staff> callers = new ArrayList<>();
        callers.add(hr);
        callers.add(account("org_data_admin"));
        callers.add(account());
        callers.add(administrator());
        for (Staff caller : callers) {
            Throwable refused = catchThrowable(() -> releaseAs(caller, held.hold(), REASON_TEXT));

            assertThat(isPermissionRefusal(refused)).as("caller " + caller.employeeId() + " lacks the permission: " + refused).isTrue();
            assertStillHeld(held);
        }
        actAsNobody();

        Throwable nobody = catchThrowable(() -> commands().release(held.hold(), REASON_TEXT));

        assertThat(isPermissionRefusal(nobody)).as("nobody signed in: " + nobody).isTrue();
        assertStillHeld(held);
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isZero();
        assertThat(eventsOf(EVENT_HOLD_RELEASED, held.command())).isZero();
    }

    @Test
    @DisplayName("#873 ADR-0068 第 1 条第 6 项（行 59）、ADR-0009（反向提权）：安全审计员有放行权限但没有录入人事命令的权限：录入离职命令被拒，不留命令行")
    void releasingDoesNotCarryTheRightToEnterCommands() throws Exception {
        Staff releaser = auditor();
        UUID ordinary = employed(REGULAR);
        actIn(releaser);

        Throwable refused = catchThrowable(() -> separate(ordinary, LAST_DAY));

        assertThat(isPermissionRefusal(refused)).as(String.valueOf(refused)).isTrue();
        assertThat(commandCount(ordinary)).isZero();
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 88）、ADR-0009（多角色）：同时持有安全审计员与人事专员的人，两项权限各自生效：既能放行，也能录入命令；放行人记为其账号")
    void aHolderOfTwoRolesHasBothPermissions() throws Exception {
        Held held = oneHold();
        Staff both = account(SECURITY_AUDITOR, PERSONNEL_SPECIALIST_ROLE);

        releaseAs(both, held.hold(), REASON_TEXT);

        assertThat(hold(held.hold()).get("released_by")).isEqualTo(both.userId());
        assertThat(employmentOn(held.employee(), TODAY)).isEqualTo(SEPARATED);
        UUID ordinary = employed(REGULAR);
        UUID entered = separate(ordinary, LAST_DAY);
        assertThat(command(entered).get("created_by")).isEqualTo(both.userId());
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 88）、第 1 条第 2、6 项（行 38 至 40、58 至 59）：放行参数里没有放行人、状态、时刻、账号等调用者可自填的成分；只有暂停记录 ID 与原因")
    void theCallerCannotSupplyWhatTheServerDecides() throws Exception {
        actIn(hr);
        List<String> components = commands().releaseComponents();

        assertThat(components).contains(SEPARATION_HOLD_ID, REASON);
        List<String> forbidden = new ArrayList<>(CALLER_MUST_NOT_SUPPLY);
        forbidden.addAll(List.of("releasedby", "releaser", "operator", "channel", "mode", "releasedat"));
        for (String component : components) {
            String lower = component.toLowerCase(Locale.ROOT);
            assertThat(forbidden).as("component " + component).noneMatch(lower::contains);
        }
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 1 项（行 79，附件 DDL ck_separation_holds_release_reason）、第 3 项（行 90）边界：放行原因为空、全空白、501 个字都被拒，暂停记录仍为 HELD；500 个字接受并原样落库")
    void theReasonIsOneToFiveHundredCharactersAndNotBlank() throws Exception {
        Held held = oneHold();
        Staff releaser = auditor();
        List<String> refusedReasons = new ArrayList<>(Arrays.asList(null, "", " ", "  \t ", "\n", "r".repeat(501)));
        for (String reason : refusedReasons) {
            Throwable refused = catchThrowable(() -> releaseAs(releaser, held.hold(), reason));

            assertThat(refused).as("reason [" + (reason == null ? null : reason.length() + " chars") + "]").isInstanceOf(RuntimeException.class);
            assertThat(isPermissionRefusal(refused)).isFalse();
            assertStillHeld(held);
        }
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isZero();

        releaseAs(releaser, held.hold(), "r".repeat(500));

        assertThat(hold(held.hold()).get("release_reason")).isEqualTo("r".repeat(500));
        assertThat(holdStatus(held.hold())).isEqualTo(RELEASED);
    }
}
