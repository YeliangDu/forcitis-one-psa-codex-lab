package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-0033 section 5 items 3 and 4: who may register a source reference by hand, and for which category only. */
class SourceReferenceManualEditIT extends SourceReferenceHttpSupport {

    static final TestDatabase postgres = TestPostgres.database(SourceReferenceManualEditIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 3 条：登记核算组织编码需 platform.integration:source-reference:update——未登录 401、无权限 403 且无写入，有权限 201")
    void registeringNeedsThePermission() throws Exception {
        UUID legalEntity = createLegalEntity("SMA", "Synthetic SMA");
        String json = body(ACCOUNTING_SYSTEM, LEGAL_ENTITY, "6101", legalEntity, TODAY);

        assertThat(postJson(null, json).getStatus()).isEqualTo(401);
        assertThat(postJson(person(14, Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE), json).getStatus())
                .as("maintaining legal entities is not maintaining their codes").isEqualTo(403);
        assertThat(postJson(APPROVER, json).getStatus()).as("approving is not registering").isEqualTo(403);
        assertThat(referencesOf(legalEntity)).isZero();

        assertThat(postJson(RECORDER_ONLY, json).getStatus()).isEqualTo(201);
        assertThat(referencesOf(legalEntity)).isEqualTo(1);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 4 条：人工登记只接受（核算系统, LEGAL_ENTITY）；组织源、老系统与其他类别一律 422，无写入且写审计")
    void onlyTheAccountingCodeOfALegalEntityIsEditable() throws Exception {
        UUID someone = UUID.fromString("01900000-0000-7000-8000-0000000000d1");
        List<String[]> refused = List.of(
                new String[] {"ORG_SOURCE", "EMPLOYEE"}, new String[] {"ORG_SOURCE", "ORG_UNIT"}, new String[] {"ORG_SOURCE", "POSITION"},
                new String[] {"LEGACY_SYSTEM", "EMPLOYEE"}, new String[] {"LEGACY_SYSTEM", "ORG_UNIT"}, new String[] {"ACCOUNTING_SYSTEM", "EMPLOYEE"},
                new String[] {"ORG_SOURCE", "LEGAL_ENTITY"});
        long rowsBefore = allReferences();

        for (String[] category : refused) {
            int auditBefore = integrationAudit().size();
            MockHttpServletResponse answer = postJson(RECORDER_WHO_ALSO_APPROVES, body(category[0], category[1], "-42", someone, TODAY));

            assertThat(answer.getStatus()).as("%s / %s: %s", category[0], category[1], answer.getContentAsString()).isEqualTo(422);
            assertThat((String) JsonPath.read(answer.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED");
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(integrationAudit())
                    .as("%s / %s: the refused attempt is audited", category[0], category[1]).hasSizeGreaterThan(auditBefore));
        }
        assertThat(allReferences()).as("nothing was written").isEqualTo(rowsBefore);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条、第 5 项第 4 条：核算组织编码须为正整数、法人须存在、生效日必填——否则 4xx 且无写入")
    void invalidInputIsRefused() throws Exception {
        UUID legalEntity = createLegalEntity("SMB", "Synthetic SMB");
        UUID noSuchLegalEntity = UUID.fromString("01900000-0000-7000-8000-0000000000d2");
        long rowsBefore = allReferences();

        for (String code : List.of("0", "-5")) {
            MockHttpServletResponse answer = postJson(RECORDER_ONLY, body(ACCOUNTING_SYSTEM, LEGAL_ENTITY, code, legalEntity, TODAY));
            assertThat(answer.getStatus()).as("code %s: %s", code, answer.getContentAsString()).isEqualTo(422);
        }
        assertThat(postJson(RECORDER_ONLY, body(ACCOUNTING_SYSTEM, LEGAL_ENTITY, "twelve", legalEntity, TODAY)).getStatus()).isBetween(400, 499);
        assertThat(postJson(RECORDER_ONLY, body(ACCOUNTING_SYSTEM, LEGAL_ENTITY, "6201", noSuchLegalEntity, TODAY)).getStatus())
                .as("an accounting code for a legal entity that does not exist").isBetween(400, 499);
        assertThat(postJson(RECORDER_ONLY, body(ACCOUNTING_SYSTEM, LEGAL_ENTITY, "6201", legalEntity, null)).getStatus())
                .as("the effective date is required").isEqualTo(422);
        assertThat(allReferences()).isEqualTo(rowsBefore);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 3 条（U-2、U-3）：两个新权限码进权限目录；登记授予组织数据管理员，确认授予业务管理员")
    void thePermissionsAndWhoHoldsThem() throws Exception {
        assertThat(Arrays.stream(Permission.values()).map(Permission::code)).contains(UPDATE, APPROVE);

        MockHttpServletResponse roles = mvc.perform(get("/api/v1/platform/roles").param("limit", "200")
                .with(person(15, Permission.Codes.PLATFORM_IDENTITY_ROLE_READ))).andReturn().getResponse();
        assertThat(roles.getStatus()).isEqualTo(200);
        List<String> orgDataAdmin = JsonPath.read(roles.getContentAsString(), "$.items[?(@.code == 'org_data_admin')].permissionCodes[*]");
        List<String> businessAdmin = JsonPath.read(roles.getContentAsString(), "$.items[?(@.code == 'business_admin')].permissionCodes[*]");
        assertThat(orgDataAdmin).contains(UPDATE);
        assertThat(businessAdmin).contains(APPROVE);
    }
}
