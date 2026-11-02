package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PERSONNEL_SPECIALIST_ROLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #980 acceptance (design note wecom-rebind-second-person.md section 6 lines 5 to 9, 11, 14, 15 and 17; user answer Q1 a): the second
 * person is any other holder of the personnel-command permission, never the requester nor the subject. Confirmation applies the whole
 * proposed profile and revokes the subject's sessions and browser identifiers in the same transaction; a base that moved or a WeCom id
 * taken meanwhile voids the request (the VOIDED status and its audit survive the 409); rejection needs a reason; decided requests take no
 * further decision.
 */
class ProfileChangeRequestDecisionIT extends ProfileChangeRequestSupport {

    static final TestDatabase postgres = TestPostgres.database(ProfileChangeRequestDecisionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String CHANGED = "organization.workforce.employee-profile.changed";

    @Test
    @DisplayName("#980 完成标准 5、17（用户答复 Q1 a）：请求人确认得 403 确认人代码；被改档案的人自己（也持人事命令权限）确认得 403；请求仍待复核；第三位人事专员可以确认")
    void onlyAThirdHolderConfirms() throws Exception {
        Staff subject = staff(PERSONNEL_SPECIALIST_ROLE);
        UUID request = requested(hr, subject.employeeId(), "Synthetic subject specialist", syntheticWecom("q1"));

        for (Staff refused : List.of(hr, subject)) {
            MockHttpServletResponse answer = confirm(refused, request);
            assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(403);
            assertThat(codeOf(answer)).isEqualTo(CONFIRMER_CODE);
        }
        assertThat(request(request).get("request_status")).isEqualTo("PENDING");

        assertThat(confirm(staff(PERSONNEL_SPECIALIST_ROLE), request).getStatus()).isBetween(200, 204);
        assertThat(request(request).get("request_status")).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("#980 完成标准 6：确认写入整份提议档案（姓名与企业微信账号），同事务作废被改人的会话与浏览器标识；一条 changed 审计，操作人为确认人、明细里有请求人与请求 ID；请求为 CONFIRMED、决定人为确认人")
    void confirmationAppliesTheWholeProfileAndRevokes() throws Exception {
        Staff subject = privileged();
        String moved = syntheticWecom("confirmed");
        UUID request = requested(hr, subject.employeeId(), "Synthetic confirmed name", moved);
        Staff second = staff(PERSONNEL_SPECIALIST_ROLE);
        int changedAudits = auditCount(CHANGED);

        MockHttpServletResponse answer = confirm(second, request);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isBetween(200, 204);
        Map<String, Object> root = root(subject.employeeId());
        assertThat(root.get("display_name")).isEqualTo("Synthetic confirmed name");
        assertThat(root.get("wecom_user_id")).isEqualTo(moved);
        assertThat(sessionStatus(subject)).as("sessions revoked").isNotEqualTo(200);
        assertThat(browserIdentifiers(subject)).as("browser identifiers revoked").isZero();
        assertThat(request(request).get("request_status")).isEqualTo("CONFIRMED");
        assertThat(fromStored(request(request).get("decided_by"))).isEqualTo(second.userId());
        assertThat(auditCount(CHANGED)).isEqualTo(changedAudits + 1);
        List<Map<String, Object>> entries = auditQuery(null, null, CHANGED);
        Map<String, Object> entry = entries.stream().filter(e -> String.valueOf(e.get("detail")).contains(request.toString())).findFirst()
                .orElseThrow(() -> new AssertionError("a changed entry naming the request " + request));
        assertThat(String.valueOf(entry.get("actorId"))).as("the confirmer acts").isEqualTo(text(second.userId()));
        assertThat(String.valueOf(entry.get("detail"))).as("the requester is in the detail").contains(text(hr.userId()));
    }

    @Test
    @DisplayName("#980 完成标准 7、15：请求后档案被其他途径改过（直接写库，如 #961 前的同步放行）：确认答 409，请求转 VOIDED BASE_CHANGED（409 后仍在），什么都不写、会话保留；一条 change-voided 审计")
    void aMovedBaseVoids() throws Exception {
        Staff subject = privileged();
        UUID request = requested(hr, subject.employeeId(), "Synthetic base moved", syntheticWecom("base"));
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set display_name = 'Synthetic renamed elsewhere' where id = ?", stored(subject.employeeId()));
        }
        Map<String, Object> before = root(subject.employeeId());
        long identifiers = browserIdentifiers(subject);
        int voidedAudits = auditCount(AUDIT_VOIDED);

        MockHttpServletResponse answer = confirm(staff(PERSONNEL_SPECIALIST_ROLE), request);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(409);
        assertThat(request(request).get("request_status")).isEqualTo("VOIDED");
        assertThat(request(request).get("void_reason_kind")).isEqualTo("BASE_CHANGED");
        assertUntouched(subject, before, identifiers);
        assertThat(auditCount(AUDIT_VOIDED)).isEqualTo(voidedAudits + 1);
    }

    @Test
    @DisplayName("#980 完成标准 8、15：提议的企业微信账号在确认前被别的员工占用：确认答 409，请求转 VOIDED WECOM_USER_ID_TAKEN，什么都不写；一条 change-voided 审计")
    void aTakenWecomIdVoids() throws Exception {
        Staff subject = privileged();
        String wanted = syntheticWecom("wanted");
        UUID request = requested(hr, subject.employeeId(), "Synthetic wants a taken id", wanted);
        Staff other = staff();
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set wecom_user_id = ? where id = ?", wanted, stored(other.employeeId()));
        }
        Map<String, Object> before = root(subject.employeeId());
        long identifiers = browserIdentifiers(subject);
        int voidedAudits = auditCount(AUDIT_VOIDED);

        MockHttpServletResponse answer = confirm(staff(PERSONNEL_SPECIALIST_ROLE), request);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(409);
        assertThat(request(request).get("request_status")).isEqualTo("VOIDED");
        assertThat(request(request).get("void_reason_kind")).isEqualTo("WECOM_USER_ID_TAKEN");
        assertUntouched(subject, before, identifiers);
        assertThat(auditCount(AUDIT_VOIDED)).isEqualTo(voidedAudits + 1);
    }

    @Test
    @DisplayName("#980 完成标准 9、15：另一位人事专员拒绝须写理由（没写被拒、仍待复核）；拒绝后什么都不写、会话保留，一条 change-rejected 审计；之后不能再确认（409）")
    void rejectionNeedsAReasonAndEndsTheRequest() throws Exception {
        Staff subject = privileged();
        UUID request = requested(hr, subject.employeeId(), "Synthetic to reject", syntheticWecom("reject"));
        Map<String, Object> before = root(subject.employeeId());
        long identifiers = browserIdentifiers(subject);
        Staff second = staff(PERSONNEL_SPECIALIST_ROLE);
        int rejectedAudits = auditCount(AUDIT_REJECTED);

        assertThat(reject(second, request, null).getStatus()).isEqualTo(400);
        assertThat(request(request).get("request_status")).isEqualTo("PENDING");
        assertThat(reject(second, request, "synthetic: not this person").getStatus()).isBetween(200, 204);

        assertThat(request(request).get("request_status")).isEqualTo("REJECTED");
        assertThat(request(request).get("decision_reason")).isEqualTo("synthetic: not this person");
        assertUntouched(subject, before, identifiers);
        assertThat(auditCount(AUDIT_REJECTED)).isEqualTo(rejectedAudits + 1);
        assertThat(confirm(staff(PERSONNEL_SPECIALIST_ROLE), request).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("#980 完成标准 14：已 CONFIRMED、REJECTED、VOIDED 的请求再确认或再拒绝都答 409，什么都不变")
    void decidedRequestsTakeNoFurtherDecision() throws Exception {
        Staff a = privileged();
        UUID confirmed = requested(hr, a.employeeId(), "Synthetic decided one", syntheticWecom("d1"));
        assertThat(confirm(staff(PERSONNEL_SPECIALIST_ROLE), confirmed).getStatus()).isBetween(200, 204);
        Staff b = privileged();
        UUID rejected = requested(hr, b.employeeId(), "Synthetic decided two", syntheticWecom("d2"));
        assertThat(reject(staff(PERSONNEL_SPECIALIST_ROLE), rejected, "synthetic: no").getStatus()).isBetween(200, 204);
        Staff c = privileged();
        UUID voided = requested(hr, c.employeeId(), "Synthetic decided three", syntheticWecom("d3"));
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set display_name = 'Synthetic moved' where id = ?", stored(c.employeeId()));
        }
        assertThat(confirm(staff(PERSONNEL_SPECIALIST_ROLE), voided).getStatus()).isEqualTo(409);

        Staff third = staff(PERSONNEL_SPECIALIST_ROLE);
        for (UUID decided : List.of(confirmed, rejected, voided)) {
            Map<String, Object> row = request(decided);
            assertThat(confirm(third, decided).getStatus()).as("confirm " + decided).isEqualTo(409);
            assertThat(reject(third, decided, "synthetic: late").getStatus()).as("reject " + decided).isEqualTo(409);
            assertThat(request(decided)).isEqualTo(row);
        }
    }

    @Test
    @DisplayName("#980 完成标准 11（多角色按用户判断、I-11）：没有人事命令权限的超级管理员（普通会话与管理员模式）都不能确认；同时持人事专员与另一角色的第三人可以；待复核列表对人事专员可见（无员工数据范围，第 5.4 条）")
    void aSuperAdministratorWithoutThePermissionCannotConfirm() throws Exception {
        Staff subject = privileged();
        UUID request = requested(hr, subject.employeeId(), "Synthetic admin tries", syntheticWecom("admin"));
        Staff admin = staff("super_admin");

        assertThat(confirm(admin, request).getStatus()).isEqualTo(403);
        assertThat(confirm(new Staff(admin.employeeId(), administratorMode(admin.session())), request).getStatus()).isEqualTo(403);
        assertThat(request(request).get("request_status")).isEqualTo("PENDING");

        MockHttpServletResponse listed = listPending(hr);
        assertThat(listed.getStatus()).as(listed.getContentAsString()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(listed.getContentAsString(), "$.items[*].requestId")).contains(request.toString());

        assertThat(confirm(staff(PERSONNEL_SPECIALIST_ROLE, "org_data_admin"), request).getStatus()).isBetween(200, 204);
    }
}
