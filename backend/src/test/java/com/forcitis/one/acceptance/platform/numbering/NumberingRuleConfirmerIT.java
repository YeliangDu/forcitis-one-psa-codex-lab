package com.forcitis.one.acceptance.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * R-1b P2 (ADR-0033 section 3 item 3 「本人」判定清单: 编号模板四眼; ADR-0027 section 2 item 4): the confirmer of a numbering template
 * version is not its author. Both are accounts, recorded and compared as user ids (UUIDs), never as employee ids.
 */
class NumberingRuleConfirmerIT extends AccountsAcceptanceSupport {

    static final String QUOTE_RULE = "/api/v1/platform/numbering-rules/QUOTE";

    static final TestDatabase postgres = TestPostgres.database(NumberingRuleConfirmerIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }


    private MockHttpServletResponse confirm(RequestPostProcessor who, int templateVersion) throws Exception {
        return mvc.perform(post(QUOTE_RULE + ":confirm").with(csrf()).with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateVersion\":" + templateVersion + "}")).andReturn().getResponse();
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 3 条 / ADR-0027 第 2 项第 4 条：编号模板四眼——作者不能确认自己保存的版本（403 PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR），作者与确认人按用户 ID 记录与比较；另一人可以确认")
    void theAuthorOfAVersionCannotConfirmIt() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID authorEmployee = newEmployeeId();
        UUID approverEmployee = newEmployeeId();
        employee(authorEmployee, unit, null);
        employee(approverEmployee, unit, null);
        grantByOperations(authorEmployee, "org_data_admin");
        grantByOperations(authorEmployee, "business_admin");
        grantByOperations(approverEmployee, "business_admin");
        SignedIn author = signIn(authorEmployee);
        SignedIn approver = signIn(approverEmployee);
        UUID authorAccount = author.userId();
        UUID approverAccount = approver.userId();
        MockHttpServletResponse rule = mvc.perform(get(QUOTE_RULE).with(session(author))).andReturn().getResponse();
        assertThat(rule.getStatus()).as(rule.getContentAsString()).isEqualTo(200);

        MockHttpServletResponse saved = mvc.perform(put(QUOTE_RULE).header("If-Match", rule.getHeader("ETag")).with(csrf()).with(session(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"{TYPE}-{LE}-{yyyyMM}-{seq:5}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1,"
                        + "\"effectiveFrom\":\"" + TODAY.plusDays(1) + "\"}")).andReturn().getResponse();
        assertThat(saved.getStatus()).as(saved.getContentAsString()).isEqualTo(200);
        int pending = JsonPath.read(saved.getContentAsString(), "$.pending.templateVersion");
        assertThat((String) JsonPath.read(saved.getContentAsString(), "$.pending.authorId")).isEqualTo(authorAccount.toString());

        MockHttpServletResponse byTheAuthor = confirm(session(author), pending);

        assertThat(byTheAuthor.getStatus()).as(byTheAuthor.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(byTheAuthor.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR");

        MockHttpServletResponse byAnother = confirm(session(approver), pending);

        assertThat(byAnother.getStatus()).as(byAnother.getContentAsString()).isEqualTo(200);
        try (Connection numbering = schemaConnection("platform_numbering"); PreparedStatement select = numbering.prepareStatement("""
                select v.author_id, v.confirmed_by from numbering_rule_versions v join numbering_rules r on r.numbering_rule_id = v.numbering_rule_id
                 where r.document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE') and v.version_number = ?""")) {
            select.setInt(1, pending);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject(1, UUID.class)).as("author_id").isEqualTo(authorAccount);
                assertThat(rs.getObject(2, UUID.class)).as("confirmed_by").isEqualTo(approverAccount);
            }
        }
    }
}
