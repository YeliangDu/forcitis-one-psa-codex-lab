package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-0033 section 5 items 1 and 6 (DA-9): the accounting organization code leaves the legal entity and lives in the source references. */
class LegalEntityAccountingCodeMovedIT extends AcceptanceSupport {

    static final String ACCOUNTING_CODE_FIELD = "accountingOrganizationNumber";

    static final TestDatabase postgres = TestPostgres.database(LegalEntityAccountingCodeMovedIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 1 条：法人表不再有核算组织编码列及其唯一与 CHECK 约束")
    void theLegalEntityTableHasNoAccountingCode() throws Exception {
        List<String> leftovers = new ArrayList<>();
        try (Connection workforce = schemaConnection("organization_workforce");
             PreparedStatement select = workforce.prepareStatement("""
                     select 'column ' || column_name from information_schema.columns
                      where table_schema = current_schema() and table_name = 'legal_entities' and column_name like '%accounting%'
                     union all
                     select 'constraint ' || conname from pg_constraint
                      where conrelid = cast('legal_entities' as regclass) and conname like '%accounting%'
                     """); ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                leftovers.add(rs.getString(1));
            }
        }
        assertThat(leftovers).isEmpty();
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 1 条：法人的创建与读取响应不再带核算组织编码，请求里带了也不落到法人上")
    void theLegalEntityResponseHasNoAccountingCode() throws Exception {
        UUID plain = createLegalEntity("ACA", "Synthetic ACA");
        MockHttpServletResponse read = mvc.perform(get(LEGAL_ENTITIES + "/" + plain).with(LEGAL_ENTITY_MAINTAINER)).andReturn().getResponse();
        assertThat(read.getStatus()).isEqualTo(200);
        assertThat(read.getContentAsString()).doesNotContain(ACCOUNTING_CODE_FIELD);

        MockHttpServletResponse withOldField = mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(LEGAL_ENTITY_MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ACB\",\"name\":\"Synthetic ACB\",\"" + ACCOUNTING_CODE_FIELD + "\":1021}")).andReturn().getResponse();
        assertThat(withOldField.getContentAsString()).doesNotContain(ACCOUNTING_CODE_FIELD);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 1 条：OpenAPI 与事件目录中法人不再带核算组织编码；OpenAPI 有来源对照的登记与确认接口")
    void contractsFollow() throws Exception {
        String openApi = Files.readString(Path.of("../frontend/packages/api/openapi.json"));
        assertThat(openApi).doesNotContain(ACCOUNTING_CODE_FIELD);
        assertThat(openApi).contains("\"/api/v1/platform/source-references\"", "/api/v1/platform/source-references/{sourceReferenceId}:confirm",
                "/api/v1/platform/source-references/{sourceReferenceId}:reject");
        for (String catalogue : List.of("../docs/architecture/events/workforce.yaml", "../docs/architecture/events/asyncapi.yaml")) {
            assertThat(Files.readString(Path.of(catalogue))).as(catalogue).doesNotContain(ACCOUNTING_CODE_FIELD).doesNotContain("LegalEntityAccountingNumberAssigned");
        }
    }
}
