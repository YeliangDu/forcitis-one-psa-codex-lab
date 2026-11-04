package com.forcitis.one.acceptance.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Names;
import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P5 ADR-0033 section 7 item 2 / ADR-0027 section 3 item 3 (card acceptance item 2): an employee's own login records are the
 * entries of kind USER_ACCOUNT whose object id is the caller's user id, and nothing else. An entry of another kind whose object id
 * happens to be the same UUID is not theirs. The action and field whitelists are unchanged.
 */
class OwnLoginRecordsBySubjectIT extends P5Support {

    static final String OWN = "/api/v1/platform/own-login-records";
    static final String SUCCEEDED = "platform.identity.login.succeeded";
    static final String FAILED = "platform.identity.login.failed";
    static final String LOGOUT = "platform.identity.logout";
    static final String ROLES_REPLACED = "platform.identity.account.roles.replaced";

    static final TestDatabase postgres = TestPostgres.database(OwnLoginRecordsBySubjectIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    // RULE-487
    @Test
    @DisplayName("R-1b P5 ADR-0033 第 7 项第 2 条 / ADR-0027 第 3 项第 3 条：员工查本人登录记录只按「用户账号 + 本人用户 ID」取——本人的登录成功、失败、登出都在；他人的不在；类别不同而对象 ID 恰为本人用户 ID 的条目不在")
    void ownRecordsAreFoundByKindAndUserId() throws Exception {
        UUID me = UUID.fromString(principalName(31));
        UUID someoneElse = UUID.fromString(principalName(32));
        auditRow(SUCCEEDED, me, P5Names.USER_ACCOUNT, me, "SUCCEEDED", "10.1.0.1", "WECOM_WORKBENCH");
        auditRow(FAILED, me, P5Names.USER_ACCOUNT, me, "FAILED", "10.1.0.2", "WECOM_WORKBENCH");
        auditRow(LOGOUT, me, P5Names.USER_ACCOUNT, me, "SUCCEEDED", "10.1.0.3", "WECOM_WORKBENCH");
        auditRow(ROLES_REPLACED, someoneElse, P5Names.USER_ACCOUNT, me, null, "10.1.0.4", null);
        auditRow(SUCCEEDED, someoneElse, P5Names.USER_ACCOUNT, someoneElse, "SUCCEEDED", "10.2.0.1", "WECOM_WORKBENCH");
        auditRow(SUCCEEDED, someoneElse, P5Names.EMPLOYEE, me, "SUCCEEDED", "10.9.9.9", "WECOM_WORKBENCH");
        auditRow(FAILED, someoneElse, P5Names.EMPLOYEE, me, "FAILED", "10.9.9.8", "WECOM_WORKBENCH");

        MockHttpServletResponse own = mvc.perform(get(OWN).with(person(31, Permission.Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ))).andReturn().getResponse();

        assertThat(own.getStatus()).as(own.getContentAsString()).isEqualTo(200);
        List<String> actions = JsonPath.read(own.getContentAsString(), "$.items[*].action");
        List<String> addresses = JsonPath.read(own.getContentAsString(), "$.items[*].sourceAddress");
        assertThat(actions).containsExactlyInAnyOrder(SUCCEEDED, FAILED, LOGOUT);
        assertThat(addresses).containsExactlyInAnyOrder("10.1.0.1", "10.1.0.2", "10.1.0.3");
        assertThat(addresses).as("an entry of another kind with the same id is not mine").doesNotContain("10.9.9.9", "10.9.9.8");
        assertThat(own.getContentAsString()).doesNotContain("subjectKind", "subjectId", "subjectKey", "actorId", "detail");
    }

    // RULE-487
    @Test
    @DisplayName("R-1b P5 ADR-0027 第 3 项第 3 条：本人登录记录的字段白名单不变（发生时间、动作、结果、来源地址、登录方式），另一人只看到自己的")
    void theFieldWhitelistIsUnchanged() throws Exception {
        UUID me = UUID.fromString(principalName(33));
        UUID other = UUID.fromString(principalName(34));
        auditRow(SUCCEEDED, me, P5Names.USER_ACCOUNT, me, "SUCCEEDED", "10.3.0.1", "WECOM_WORKBENCH");
        auditRow(SUCCEEDED, other, P5Names.USER_ACCOUNT, other, "SUCCEEDED", "10.4.0.1", "SSO");

        MockHttpServletResponse own = mvc.perform(get(OWN).with(person(33, Permission.Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ))).andReturn().getResponse();
        MockHttpServletResponse theirs = mvc.perform(get(OWN).with(person(34, Permission.Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ))).andReturn().getResponse();

        List<Map<String, Object>> mine = JsonPath.read(own.getContentAsString(), "$.items");
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().keySet()).containsExactlyInAnyOrderElementsOf(Set.of("occurredAt", "action", "outcome", "sourceAddress", "loginMethod"));
        assertThat((List<String>) JsonPath.read(theirs.getContentAsString(), "$.items[*].sourceAddress")).containsExactly("10.4.0.1");
    }
}
