package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** With the single operator mode on, the in-application second-person rules and the database constraint behind them are as strict as without it. */
@Import(SingleOperatorIdentitySupport.Switches.class)
class SingleOperatorTwoPersonRegressionIT extends AccountsAcceptanceSupport {

    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    static final String REQUESTS = "/api/v1/platform/authorization-requests";

    static final TestDatabase postgres = TestPostgres.database(SingleOperatorTwoPersonRegressionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    SingleOperatorIdentitySupport.ModeSwitch modeSwitch;

    UUID unit;

    @BeforeEach
    void modeOnAndADepartment() throws Exception {
        modeSwitch.turn(true);
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
    }

    private SignedIn administrator() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        grantByOperations(person, "super_admin");
        return administratorMode(signIn(person));
    }

    private MockHttpServletResponse replaceRoles(SignedIn requester, SignedIn target, String... roleCodes) throws Exception {
        MockHttpServletResponse account = mvc.perform(get(ACCOUNTS + target.userId()).with(session(requester))).andReturn().getResponse();
        assertThat(account.getStatus()).as(account.getContentAsString()).isEqualTo(200);
        return mvc.perform(post(ACCOUNTS + target.userId() + ":replace-roles").header("If-Match", account.getHeader("ETag")).with(csrf()).with(session(requester))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"" + String.join("\",\"", roleCodes) + "\"]}")).andReturn().getResponse();
    }

    private MockHttpServletResponse decide(SignedIn who, String requestId) throws Exception {
        return mvc.perform(post(REQUESTS + "/" + requestId + ":confirm").with(csrf()).with(session(who))).andReturn().getResponse();
    }

    private List<String> rolesOf(SignedIn reader, SignedIn account) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + account.userId()).with(session(reader))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return JsonPath.read(read.getContentAsString(), "$.roleCodes");
    }

    @Test
    @DisplayName("SINGLE-OP P2-23 模式开启下应用内敏感授予仍待第二人：申请人与受益人确认都是 403 PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED，第三人确认后才生效")
    void p223_sensitiveGrantStillNeedsASecondPerson() throws Exception {
        SignedIn requester = administrator();
        SignedIn beneficiary = administrator();
        SignedIn third = administrator();

        MockHttpServletResponse accepted = replaceRoles(requester, beneficiary, "employee", "super_admin", "security_auditor");

        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(202);
        String requestId = JsonPath.read(accepted.getContentAsString(), "$.requestId");
        for (SignedIn refused : List.of(requester, beneficiary)) {
            MockHttpServletResponse answer = decide(refused, requestId);
            assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(403);
            assertThat((String) JsonPath.read(answer.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED");
        }
        assertThat(rolesOf(third, beneficiary)).doesNotContain("security_auditor");

        assertThat(decide(third, requestId).getStatus()).isEqualTo(204);
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> assertThat(rolesOf(third, beneficiary)).contains("security_auditor"));
    }

    @Test
    @DisplayName("SINGLE-OP P2-23 模式开启下 I-12 仍有效：管理员给自己授任何角色被拒（403 PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN），角色不变")
    void p223_noSelfGrantEither() throws Exception {
        SignedIn administrator = administrator();

        MockHttpServletResponse self = replaceRoles(administrator, administrator, "employee", "super_admin", "security_auditor");

        assertThat(self.getStatus()).as(self.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(self.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN");
        assertThat(rolesOf(administrator, administrator)).doesNotContain("security_auditor");
    }

    private void updateRequest(String set, String requestId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement update = identity.prepareStatement(
                "update platform_identity.authorization_requests set " + set + " where request_id = ?")) {
            update.setObject(1, UUID.fromString(requestId));
            update.executeUpdate();
        }
    }

    @Test
    @DisplayName("SINGLE-OP P2-23 模式开启下直接改库仍被约束拦住：把申请人或受益人写成确认人并置已确认，违反 ck_authorization_requests_two_people，申请仍待确认")
    void p223_theDatabaseConstraintStillHolds() throws Exception {
        SignedIn requester = administrator();
        SignedIn beneficiary = administrator();
        MockHttpServletResponse accepted = replaceRoles(requester, beneficiary, "employee", "super_admin", "security_auditor");
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(202);
        String requestId = JsonPath.read(accepted.getContentAsString(), "$.requestId");

        assertThatThrownBy(() -> updateRequest("request_status = 'CONFIRMED', decided_by = requested_by, decided_at = now()", requestId)).isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_authorization_requests_two_people");
        assertThatThrownBy(() -> updateRequest("request_status = 'CONFIRMED', decided_by = subject_user_id, decided_at = now()", requestId)).isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_authorization_requests_two_people");
        MockHttpServletResponse listed = mvc.perform(get(REQUESTS).with(session(requester))).andReturn().getResponse();
        assertThat((List<String>) JsonPath.read(listed.getContentAsString(), "$.items[?(@.requestId == '" + requestId + "')].requestId")).as("still listed as waiting").containsExactly(requestId);
    }
}
