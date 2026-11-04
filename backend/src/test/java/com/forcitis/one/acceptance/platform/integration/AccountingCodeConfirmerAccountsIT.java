package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * R-1b P2 review P2-M2 (ADR-0033 section 3 item 3 「本人」判定清单: 核算组织编码四眼; section 5 item 4): the person who registered an
 * accounting organization code cannot confirm it. Recorder and confirmer are real accounts (WeCom login, UUIDs that equal no employee
 * id) holding the seeded roles granted with the operations command, acting in their own sessions (RULING-P2-8); recorded and compared
 * as user ids.
 */
class AccountingCodeConfirmerAccountsIT extends AccountsAcceptanceSupport {

    static final String SOURCE_REFERENCES = "/api/v1/platform/source-references";

    static final TestDatabase postgres = TestPostgres.database(AccountingCodeConfirmerAccountsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private MockHttpServletResponse confirm(SignedIn who, String sourceReferenceId, String etag) throws Exception {
        MockHttpServletRequestBuilder request = post(SOURCE_REFERENCES + "/" + sourceReferenceId + ":confirm").with(csrf()).with(session(who));
        if (etag != null) {
            request.header("If-Match", etag);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    // RULE-463
    @Test
    @DisplayName("P2-M2 核算组织编码四眼（真实账号、生产会话，按用户 ID 比较）：登记人不能确认自己登记的编码（403 PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR），另一人可以；登记人与确认人记为两人的 uuid")
    void theRecorderCannotConfirmTheirOwnAccountingCode() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID recorderEmployee = newEmployeeId();
        UUID approverEmployee = newEmployeeId();
        employee(recorderEmployee, unit, null);
        employee(approverEmployee, unit, null);
        grantByOperations(recorderEmployee, "org_data_admin");
        grantByOperations(recorderEmployee, "business_admin");
        grantByOperations(approverEmployee, "business_admin");
        SignedIn recorder = signIn(recorderEmployee);
        SignedIn approver = signIn(approverEmployee);
        UUID legalEntity = createLegalEntity(recorder, newLegalEntityCode("AC"), "Synthetic accounting code");

        MockHttpServletResponse registered = mvc.perform(post(SOURCE_REFERENCES).with(csrf()).with(session(recorder))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sourceSystem\":\"ACCOUNTING_SYSTEM\",\"sourceObjectKind\":\"LEGAL_ENTITY\",\"sourceId\":\"1021\","
                        + "\"ownId\":\"" + legalEntity + "\",\"effectiveFrom\":\"" + TODAY + "\"}")).andReturn().getResponse();
        assertThat(registered.getStatus()).as(registered.getContentAsString()).isEqualTo(201);
        String row = JsonPath.read(registered.getContentAsString(), "$.sourceReferenceId");

        MockHttpServletResponse bySelf = confirm(recorder, row, registered.getHeader("ETag"));

        assertThat(bySelf.getStatus()).as(bySelf.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(bySelf.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR");

        MockHttpServletResponse byAnother = confirm(approver, row, registered.getHeader("ETag"));

        assertThat(byAnother.getStatus() / 100).as(byAnother.getContentAsString()).isEqualTo(2);
        try (Connection integration = schemaConnection("platform_integration");
             PreparedStatement select = integration.prepareStatement("select confirmation_status, created_by, decided_by from source_references where source_reference_id = ?")) {
            select.setObject(1, UUID.fromString(row));
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("CONFIRMED");
                assertThat(rs.getObject(2, UUID.class)).as("created_by").isEqualTo(recorder.userId());
                assertThat(rs.getObject(3, UUID.class)).as("decided_by").isEqualTo(approver.userId());
            }
        }
    }

    @Test
    @DisplayName("P2-M2 驳回同样按真实账号记账：驳回后 decided_by 是驳回人，created_by 仍是登记人")
    void theRejecterIsRecordedAsTheDecider() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID recorderEmployee = newEmployeeId();
        UUID rejecterEmployee = newEmployeeId();
        employee(recorderEmployee, unit, null);
        employee(rejecterEmployee, unit, null);
        grantByOperations(recorderEmployee, "org_data_admin");
        grantByOperations(recorderEmployee, "business_admin");
        grantByOperations(rejecterEmployee, "business_admin");
        SignedIn recorder = signIn(recorderEmployee);
        SignedIn rejecter = signIn(rejecterEmployee);
        UUID legalEntity = createLegalEntity(recorder, newLegalEntityCode("AR"), "Synthetic rejected accounting code");

        MockHttpServletResponse registered = mvc.perform(post(SOURCE_REFERENCES).with(csrf()).with(session(recorder))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sourceSystem\":\"ACCOUNTING_SYSTEM\",\"sourceObjectKind\":\"LEGAL_ENTITY\",\"sourceId\":\"1022\","
                        + "\"ownId\":\"" + legalEntity + "\",\"effectiveFrom\":\"" + TODAY + "\"}")).andReturn().getResponse();
        assertThat(registered.getStatus()).as(registered.getContentAsString()).isEqualTo(201);
        String row = JsonPath.read(registered.getContentAsString(), "$.sourceReferenceId");

        MockHttpServletResponse rejected = mvc.perform(post(SOURCE_REFERENCES + "/" + row + ":reject").with(csrf()).with(session(rejecter))
                .header("If-Match", registered.getHeader("ETag"))).andReturn().getResponse();

        assertThat(rejected.getStatus() / 100).as(rejected.getContentAsString()).isEqualTo(2);
        try (Connection integration = schemaConnection("platform_integration");
             PreparedStatement select = integration.prepareStatement("select confirmation_status, created_by, decided_by from source_references where source_reference_id = ?")) {
            select.setObject(1, UUID.fromString(row));
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("REJECTED");
                assertThat(rs.getObject(2, UUID.class)).as("created_by").isEqualTo(recorder.userId());
                assertThat(rs.getObject(3, UUID.class)).as("decided_by").isEqualTo(rejecter.userId());
            }
        }
    }
}
