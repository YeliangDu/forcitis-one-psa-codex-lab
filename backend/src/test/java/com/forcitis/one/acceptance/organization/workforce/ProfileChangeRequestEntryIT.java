package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PERSONNEL_SPECIALIST_ROLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #980 acceptance (design note wecom-rebind-second-person.md section 6 lines 1 to 4, 10, 11, 13, 15 and 16; user intent of 2026-11-02, three
 * controls; user answer Q2 a): a rebind or clear of a set WeCom user id on an account with a role beyond employee no longer is refused but
 * waits for a second person (202 with a request id; nothing written, nothing revoked); everything else stays one person as #959 shipped it.
 * While a request is pending any profile change of that employee answers 409; the requester may withdraw. Within 30 days after a
 * second-person clear, a fill-in from empty on such an account also waits.
 */
class ProfileChangeRequestEntryIT extends ProfileChangeRequestSupport {

    static final TestDatabase postgres = TestPostgres.database(ProfileChangeRequestEntryIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String CHANGED = "organization.workforce.employee-profile.changed";

    @AfterEach
    void putTheClockBack() {
        setToday(TODAY);
    }

    private void assertWaits(Staff subject, String newWecom) throws Exception {
        Map<String, Object> before = root(subject.employeeId());
        long identifiers = browserIdentifiers(subject);
        int requestedAudits = auditCount(AUDIT_REQUESTED);
        int changedAudits = auditCount(CHANGED);

        UUID request = requested(hr, subject.employeeId(), "Synthetic waiting " + newWecom, newWecom);

        assertUntouched(subject, before, identifiers);
        Map<String, Object> row = request(request);
        assertThat(row.get("request_status")).isEqualTo("PENDING");
        assertThat(row.get("base_wecom_user_id")).isEqualTo(wecomOf(subject));
        assertThat(row.get("wecom_user_id")).isEqualTo(newWecom);
        assertThat(fromStored(row.get("requested_by"))).isEqualTo(hr.userId());
        assertThat(requestsOf(subject.employeeId())).isEqualTo(1);
        assertThat(auditCount(AUDIT_REQUESTED)).isEqualTo(requestedAudits + 1);
        assertThat(auditCount(CHANGED)).isEqualTo(changedAudits);
    }

    @Test
    @DisplayName("#980 完成标准 1、15：有普通员工以外角色的账号，改绑已有的企业微信账号：202 带请求 ID；档案、会话、浏览器标识都不变；一条待复核请求（基准为原账号、提议为新账号、请求人为人事专员）；一条 change-requested 审计，没有 changed 审计")
    void aPrivilegedRebindWaits() throws Exception {
        assertWaits(privileged(), syntheticWecom("rebind"));
    }

    @Test
    @DisplayName("#980 完成标准 2、15：有角色的账号清空已有的企业微信账号：同完成标准 1，提议值为空")
    void aPrivilegedClearWaits() throws Exception {
        assertWaits(privileged(), null);
    }

    @Test
    @DisplayName("#980 完成标准 3（#959 回归）：只有普通员工角色的账号改绑：一人即生效（200），会话与浏览器标识同事务作废；有角色的账号从空补填（30 天规则之外）也一人生效；都不留请求")
    void employeeOnlyAndFillInStayOnePerson() throws Exception {
        Staff plain = staff();
        browserIdentifier(plain);
        String moved = syntheticWecom("plain");

        MockHttpServletResponse answer = changeOver(hr, plain.employeeId(), "Synthetic plain moved", moved);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(root(plain.employeeId()).get("wecom_user_id")).isEqualTo(moved);
        assertThat(sessionStatus(plain)).isNotEqualTo(200);
        assertThat(browserIdentifiers(plain)).isZero();
        assertThat(requestsOf(plain.employeeId())).isZero();

        Staff empty = privileged();
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set wecom_user_id = null where id = ?", stored(empty.employeeId()));
        }
        String filled = syntheticWecom("fill-in");
        MockHttpServletResponse fillIn = changeOver(hr, empty.employeeId(), "Synthetic filled in", filled);
        assertThat(fillIn.getStatus()).as(fillIn.getContentAsString()).isEqualTo(200);
        assertThat(root(empty.employeeId()).get("wecom_user_id")).isEqualTo(filled);
        assertThat(requestsOf(empty.employeeId())).isZero();
    }

    @Test
    @DisplayName("#980 完成标准 4：有角色的账号只改姓名（企业微信账号原样）：一人即生效、不留请求、会话保留")
    void aPrivilegedChangeWithoutAWecomMoveAppliesAtOnce() throws Exception {
        Staff subject = privileged();

        MockHttpServletResponse answer = changeOver(hr, subject.employeeId(), "Synthetic renamed only", wecomOf(subject));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(root(subject.employeeId()).get("display_name")).isEqualTo("Synthetic renamed only");
        assertThat(requestsOf(subject.employeeId())).isZero();
        assertThat(sessionStatus(subject)).isEqualTo(200);
    }

    @Test
    @DisplayName("#980 完成标准 10：有待复核请求时，该员工的任何档案修改（只改姓名、再次改绑）都答 409 PROFILE_CHANGE_PENDING，不留第二行、档案不变")
    void anyChangeWhilePendingIsRefused() throws Exception {
        Staff subject = privileged();
        requested(hr, subject.employeeId(), "Synthetic pending", syntheticWecom("pending"));
        Map<String, Object> before = root(subject.employeeId());

        for (String wecom : new String[] {wecomOf(subject), syntheticWecom("again")}) {
            MockHttpServletResponse answer = changeOver(hr, subject.employeeId(), "Synthetic meanwhile", wecom);
            assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(409);
            assertThat(codeOf(answer)).isEqualTo(PENDING_CODE);
        }

        assertThat(requestsOf(subject.employeeId())).isEqualTo(1);
        assertThat(root(subject.employeeId())).isEqualTo(before);
    }

    @Test
    @DisplayName("#980 完成标准 13、15：请求人可撤回自己的待复核请求（必须写理由，没写被拒）；撤回为 REJECTED、写 change-rejected 审计；之后可以重新修改")
    void theRequesterWithdraws() throws Exception {
        Staff subject = privileged();
        UUID request = requested(hr, subject.employeeId(), "Synthetic to withdraw", syntheticWecom("withdraw"));
        int rejectedAudits = auditCount(AUDIT_REJECTED);

        assertThat(reject(hr, request, null).getStatus()).as("a reason is required").isEqualTo(400);
        MockHttpServletResponse withdrawn = reject(hr, request, "synthetic: typed the wrong id");

        assertThat(withdrawn.getStatus()).as(withdrawn.getContentAsString()).isBetween(200, 204);
        assertThat(request(request).get("request_status")).isEqualTo("REJECTED");
        assertThat(fromStored(request(request).get("decided_by"))).isEqualTo(hr.userId());
        assertThat(auditCount(AUDIT_REJECTED)).isEqualTo(rejectedAudits + 1);
        assertThat(changeOver(hr, subject.employeeId(), "Synthetic after withdrawal", wecomOf(subject)).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("#980 完成标准 16（用户答复 Q2 a）：经第二人确认清空后，第 30 天从空补填仍须第二人（202），第 31 天一人即可（200）")
    void aFillInWithinThirtyDaysOfAConfirmedClearWaits() throws Exception {
        Staff subject = privileged();
        UUID clear = requested(hr, subject.employeeId(), "Synthetic cleared", null);
        Staff second = staff(PERSONNEL_SPECIALIST_ROLE);
        assertThat(confirm(second, clear).getStatus()).isBetween(200, 204);
        assertThat(root(subject.employeeId()).get("wecom_user_id")).isNull();

        setToday(TODAY.plusDays(30));
        Staff caller = staff(PERSONNEL_SPECIALIST_ROLE);
        UUID waiting = requested(caller, subject.employeeId(), "Synthetic day 30", syntheticWecom("day30"));
        assertThat(request(waiting).get("base_wecom_user_id")).as("a fill-in caught by the 30-day rule has an empty base").isNull();
        assertThat(reject(caller, waiting, "synthetic: try again tomorrow").getStatus()).isBetween(200, 204);

        setToday(TODAY.plusDays(31));
        MockHttpServletResponse dayThirtyOne = changeOver(staff(PERSONNEL_SPECIALIST_ROLE), subject.employeeId(), "Synthetic day 31", syntheticWecom("day31"));
        assertThat(dayThirtyOne.getStatus()).as(dayThirtyOne.getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("#980 完成标准 16：一人清空（当时账号只有普通员工角色）、或清空请求被拒或作废之后，有角色的账号从空补填都是一人即可")
    void aFillInAfterAClearThatWasNotConfirmedBySecondPersonIsOnePerson() throws Exception {
        Staff onePersonClear = staff();
        assertThat(changeOver(hr, onePersonClear.employeeId(), "Synthetic cleared alone", null).getStatus()).isEqualTo(200);
        grantByOperations(onePersonClear.employeeId(), "org_data_admin");
        assertThat(changeOver(hr, onePersonClear.employeeId(), "Synthetic filled", syntheticWecom("after-alone")).getStatus()).isEqualTo(200);

        Staff rejectedClear = privileged();
        UUID clear = requested(hr, rejectedClear.employeeId(), "Synthetic clear asked", null);
        assertThat(reject(staff(PERSONNEL_SPECIALIST_ROLE), clear, "synthetic: no").getStatus()).isBetween(200, 204);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set wecom_user_id = null where id = ?", stored(rejectedClear.employeeId()));
        }
        assertThat(changeOver(hr, rejectedClear.employeeId(), "Synthetic filled after rejection", syntheticWecom("after-rejected")).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("#980 完成标准 11（越权）：没有人事命令权限的人，改档案、确认、拒绝、查看待复核列表都得 403")
    void withoutThePermissionAllFourEndpointsAnswerForbidden() throws Exception {
        Staff subject = privileged();
        UUID request = requested(hr, subject.employeeId(), "Synthetic forbidden", syntheticWecom("forbidden"));
        Staff outsider = staff("business_admin");

        assertThat(changeOver(outsider, staff().employeeId(), "Synthetic outsider", null).getStatus()).isEqualTo(403);
        assertThat(confirm(outsider, request).getStatus()).isEqualTo(403);
        assertThat(reject(outsider, request, "synthetic").getStatus()).isEqualTo(403);
        assertThat(listPending(outsider).getStatus()).isEqualTo(403);
        assertThat(request(request).get("request_status")).isEqualTo("PENDING");
    }
}
