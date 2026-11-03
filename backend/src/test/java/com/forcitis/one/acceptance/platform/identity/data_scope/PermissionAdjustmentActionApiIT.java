package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #730 S3 (F-1 contract items 12, 12a, 12c, 13, 14, 16; constraint table rows 13, 14, 19-23, 27, 28; ADR-0044 section 2): upholding and
 * marking adjusted over HTTP: who may, what is refused with which status and leaves the item untouched, the expiry the system computes,
 * and the audit entries. A refusal is always checked against the stored row, not only the status.
 */
class PermissionAdjustmentActionApiIT extends PermissionAdjustmentApiSupport {

    static final TestDatabase postgres = TestPostgres.database(PermissionAdjustmentActionApiIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void organisation() throws Exception {
        apiWorld();
    }

    private UUID openSensitive() throws Exception {
        return sensitiveItemAbout("mover");
    }

    private String owner() {
        return employeeIdOf("owner");
    }

    private void assertUntouched(UUID item) throws Exception {
        Map<String, Object> row = rowOf(item);
        assertThat(row).containsEntry("status", "OPEN");
        assertThat(row.get("upheld_by")).isNull();
        assertThat(row.get("adjusted_by")).isNull();
        assertThat(row.get("uphold_reason")).isNull();
    }

    @Test
    @DisplayName("#730 S3 契约第 12 条：组织数据管理员维持敏感角色复核，原因加业务负责人（员工 ID），200，状态 UPHELD，应答带业务负责人与有效至，库里维持人为办理人本人账号、原因与业务负责人如实写入；此人的角色不变（不变量 27）")
    void anAdministratorUpholdsASensitiveReview() throws Exception {
        UUID item = openSensitive();
        List<String> rolesBefore = roleCodesOf(administrator("admin1"), "mover");

        MockHttpServletResponse upheld = uphold(session("oda"), item.toString(), uphold("still needed for the audit", owner()));

        assertThat(upheld.getStatus()).as(upheld.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(upheld.getContentAsString(), "$.status")).isEqualTo("UPHELD");
        assertThat((String) JsonPath.read(upheld.getContentAsString(), "$.businessOwnerEmployeeId")).isEqualTo(owner());
        assertThat((String) JsonPath.read(upheld.getContentAsString(), "$.upholdExpiresAt")).isNotBlank();
        Map<String, Object> row = rowOf(item);
        assertThat(row).containsEntry("status", "UPHELD").containsEntry("uphold_reason", "still needed for the audit");
        assertThat(row.get("upheld_by").toString()).isEqualTo(userId("oda"));
        assertThat(row.get("business_owner_employee_id").toString()).isEqualTo(owner());
        assertThat(roleCodesOf(administrator("admin1"), "mover")).as("upholding changes no grant").containsExactlyInAnyOrderElementsOf(rolesBefore);
    }

    @Test
    @DisplayName("#730 S3 契约第 12a 条（ADR-0044 第 2 项第 2 条）：有效至 = 维持之后下一自然季度首日 00:00（Asia/Shanghai）：11-02 维持得 2026-12-31T16:00Z；12-31 23:59 维持仍得 2026-12-31T16:00Z；恰在季度首日 00:00 维持得再下一季度；10-01 00:00 维持得 2026-12-31T16:00Z")
    void theExpiryIsTheNextQuarterStartInShanghai() throws Exception {
        Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("2026-11-02T02:00:00Z", "2026-12-31T16:00:00Z");
        expected.put("2026-12-31T15:59:00Z", "2026-12-31T16:00:00Z");
        expected.put("2026-12-31T16:00:00Z", "2027-03-31T16:00:00Z");
        expected.put("2026-09-30T16:00:00Z", "2026-12-31T16:00:00Z");
        for (Map.Entry<String, String> at : expected.entrySet()) {
            UUID item = openSensitive();
            advanceTo(Instant.parse(at.getKey()));

            MockHttpServletResponse upheld = uphold(session("oda"), item.toString(), uphold("quarter boundary", owner()));

            assertThat(upheld.getStatus()).as("upheld at " + at.getKey() + ": " + upheld.getContentAsString()).isEqualTo(200);
            assertThat(instantAt(upheld, "$.upholdExpiresAt")).as("upheld at " + at.getKey()).isEqualTo(Instant.parse(at.getValue()));
            assertThat(rowOf(item)).containsEntry("uphold_expires_at", at.getValue());
        }
    }

    @Test
    @DisplayName("#730 S3 契约第 12a 条：办理人不能填有效至——请求里带了有效至时要么 400 且条目不变，要么忽略它、有效至仍是系统算的下一季度首日")
    void theHandlerCannotChooseTheExpiry() throws Exception {
        UUID item = openSensitive();
        String body = "{\"reason\":\"choose my own expiry\",\"businessOwnerEmployeeId\":\"" + owner() + "\",\"upholdExpiresAt\":\"2030-01-01T00:00:00Z\"}";

        MockHttpServletResponse answer = uphold(session("oda"), item.toString(), body);

        if (answer.getStatus() == 400) {
            assertUntouched(item);
        } else {
            assertThat(answer.getStatus()).isEqualTo(200);
            assertThat(instantAt(answer, "$.upholdExpiresAt")).isEqualTo(Instant.parse("2026-12-31T16:00:00Z"));
        }
    }

    @Test
    @DisplayName("#730 S3 契约第 12 条：业务负责人可以是办理人本人")
    void theBusinessOwnerMayBeTheHandler() throws Exception {
        UUID item = openSensitive();

        MockHttpServletResponse upheld = uphold(session("oda"), item.toString(), uphold("I own this need", employeeIdOf("oda")));

        assertThat(upheld.getStatus()).as(upheld.getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("#730 S3 不变量 14、契约第 12 条的 400：缺原因、空原因、只有空格、只有制表符、超过 500 字，以及缺业务负责人、ID 形式不对、不存在的人、已离职的人、当事人本人，都是 400 且条目不变")
    void invalidUpholdRequestsAreRefusedAndChangeNothing() throws Exception {
        UUID item = openSensitive();
        String o = owner();
        List<String> bodies = List.of(
                "{\"businessOwnerEmployeeId\":\"" + o + "\"}",
                uphold("", o),
                uphold("   ", o),
                uphold("\\t\\t", o),
                uphold("x".repeat(501), o),
                "{\"reason\":\"no owner\"}",
                uphold("bad owner id", "not-an-id"),
                uphold("unknown owner", UUID.randomUUID().toString()),
                uphold("separated owner", employeeIdOf("gone")),
                uphold("owner is the subject", employeeIdOf("mover")));

        for (String body : bodies) {
            MockHttpServletResponse answer = commandWithCurrentVersion(session("oda"), item, "uphold", body);
            assertThat(answer.getStatus()).as(body.length() > 200 ? "501 characters" : body).isEqualTo(400);
            assertUntouched(item);
        }
    }

    @Test
    @DisplayName("#730 S3 不变量 14 边界：恰好 500 字的原因被接受")
    void aReasonOfExactlyFiveHundredCharactersIsAccepted() throws Exception {
        UUID item = openSensitive();

        MockHttpServletResponse upheld = uphold(session("oda"), item.toString(), uphold("x".repeat(500), owner()));

        assertThat(upheld.getStatus()).as(upheld.getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("#730 S3 ADR-0035 第 8 项：维持必带 If-Match——缺失 428，版本过期 412，两种都不改条目")
    void upholdNeedsTheCurrentVersion() throws Exception {
        UUID item = openSensitive();
        String body = uphold("version check", owner());

        assertThat(command(session("oda"), item, "uphold", null, body).getStatus()).isEqualTo(428);
        assertThat(command(session("oda"), item, "uphold", "\"999999\"", body).getStatus()).isEqualTo(412);
        assertUntouched(item);
    }

    @Test
    @DisplayName("#730 S3 契约第 13 条与提权：安全审计员、无角色者、原部门与新部门负责人、当事人本人都不能维持敏感角色复核（403，条目不变）；组织数据管理员维持关于自己的条目是 403 PLATFORM_IDENTITY_OWN_PERMISSION_ADJUSTMENT_FORBIDDEN（不变量 19）")
    void onlyAnotherAdministratorMayUpholdASensitiveReview() throws Exception {
        UUID item = openSensitive();
        UUID own = sensitiveItemAbout("odaSubject");
        String body = uphold("not allowed", owner());

        for (String caller : List.of("auditor", "plain", "headA", "headB", "mover")) {
            MockHttpServletResponse answer = commandWithCurrentVersion(session(caller), item, "uphold", body);
            assertThat(answer.getStatus()).as(caller).isEqualTo(403);
            assertUntouched(item);
        }
        MockHttpServletResponse ownAnswer = command(session("odaSubject"), own, "uphold", read(session("oda"), own).getHeader("ETag"), body);
        assertThat(ownAnswer.getStatus()).as(ownAnswer.getContentAsString()).isEqualTo(403);
        assertThat(errorCode(ownAnswer)).isEqualTo(OWN_ITEM_ERROR);
        assertUntouched(own);
    }

    @Test
    @DisplayName("#730 S3 契约第 12 条的审计：维持写 PermissionAdjustmentUpheld（对象类别 PERMISSION_ADJUSTMENT、对象为该条目、操作人为办理人），文字含业务负责人的员工 ID，不含姓名")
    void upholdingIsAudited() throws Exception {
        UUID item = openSensitive();
        assertThat(uphold(session("oda"), item.toString(), uphold("audited uphold", owner())).getStatus()).isEqualTo(200);

        eventually(() -> {
            List<Map<String, Object>> entries = auditEntries(UPHELD_ACTION);
            assertThat(entries).anySatisfy(entry -> {
                assertThat(entry).containsEntry("subjectKind", "PERMISSION_ADJUSTMENT").containsEntry("subjectId", item.toString())
                        .containsEntry("actorId", userId("oda"));
                assertThat(auditText(entry)).contains(owner()).doesNotContain("Synthetic");
            });
        });
    }

    @Test
    @DisplayName("#730 S3 状态机（OPEN 才能转换）：已维持的条目再维持、再标记已调整都不是 2xx，维持人与原因不变")
    void anUpheldItemTakesNoFurtherCommand() throws Exception {
        UUID item = openSensitive();
        assertThat(uphold(session("oda"), item.toString(), uphold("first", owner())).getStatus()).isEqualTo(200);
        Map<String, Object> before = rowOf(item);

        MockHttpServletResponse again = commandWithCurrentVersion(session("oda"), item, "uphold", uphold("second", owner()));
        MockHttpServletResponse adjust = commandWithCurrentVersion(session("oda"), item, "mark-adjusted", adjustedBy("auditEntryId", anAuditEntryId()));

        assertThat(again.getStatus()).isBetween(400, 499);
        assertThat(adjust.getStatus()).isBetween(400, 499);
        assertThat(rowOf(item)).containsEntry("status", "UPHELD").containsEntry("uphold_reason", "first").containsEntry("upheld_by", before.get("upheld_by"));
    }

    @Test
    @DisplayName("#730 S3 契约第 16 条来源①（ADR-0044 Q1 (c)）：新部门负责人只凭原因维持范围复核，200、UPHELD，应答没有有效至、没有业务负责人；组织数据管理员、安全审计员、原部门负责人、无关者对收件人不为空的条目都是 403")
    void onlyTheRecipientUpholdsATransferScopeReview() throws Exception {
        UUID item = scopeReviewAbout("mover", "headB");
        String body = reasonOnly("the new unit needs this scope");

        for (String caller : List.of("oda", "auditor", "headA", "plain", "multi")) {
            assertThat(commandWithCurrentVersion(session(caller), item, "uphold", body).getStatus()).as(caller).isEqualTo(403);
            assertUntouched(item);
        }
        MockHttpServletResponse upheld = commandWithCurrentVersion(session("headB"), item, "uphold", body);

        assertThat(upheld.getStatus()).as(upheld.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(upheld.getContentAsString(), "$.status")).isEqualTo("UPHELD");
        assertThat(upheld.getContentAsString()).doesNotContain("upholdExpiresAt\":\"").doesNotContain("businessOwnerEmployeeId\":\"");
        Map<String, Object> row = rowOf(item);
        assertThat(row.get("uphold_expires_at")).isNull();
        assertThat(row.get("business_owner_employee_id")).isNull();
        assertThat(row.get("upheld_by").toString()).isEqualTo(userId("headB"));
    }

    @Test
    @DisplayName("#730 S3 契约第 16 条与 N1-09：收件人为空的范围复核由组织数据管理员办理（原因即可），其他部门负责人 403；多重角色者（安全审计员兼新部门负责人）可维持收件人是自己的条目")
    void anItemWithoutARecipientGoesToTheAdministratorAndTwoRolesAddUp() throws Exception {
        UUID noHead = scopeReviewAbout("mover", null);
        UUID forMulti = scopeReviewAbout("mover2", "multi");

        assertThat(commandWithCurrentVersion(session("headB"), noHead, "uphold", reasonOnly("not the recipient")).getStatus()).isEqualTo(403);
        assertUntouched(noHead);
        assertThat(commandWithCurrentVersion(session("oda"), noHead, "uphold", reasonOnly("forwarded to me")).getStatus()).isEqualTo(200);
        assertThat(commandWithCurrentVersion(session("multi"), forMulti, "uphold", reasonOnly("as the head of the new unit")).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("#730 S3 契约第 16 条来源②（RULE-387）：角色条目由组织数据管理员凭原因维持（无有效至），安全审计员只读（403），新部门负责人与无关者 403")
    void anInactiveUnitItemIsHandledByTheAdministratorOnly() throws Exception {
        UUID item = inactiveUnitItem();
        String body = reasonOnly("the role still needs the unit");

        for (String caller : List.of("auditor", "headB", "plain")) {
            assertThat(commandWithCurrentVersion(session(caller), item, "uphold", body).getStatus()).as(caller).isEqualTo(403);
            assertUntouched(item);
        }
        MockHttpServletResponse upheld = commandWithCurrentVersion(session("oda"), item, "uphold", body);

        assertThat(upheld.getStatus()).as(upheld.getContentAsString()).isEqualTo(200);
        assertThat(rowOf(item).get("uphold_expires_at")).isNull();
    }

    private String anAuditEntryId() throws Exception {
        String mover = userId("mover");
        String moverEmployee = employeeIdOf("mover");
        return auditEntries("platform.identity.operations.account.role.granted").stream()
                .filter(entry -> auditText(entry).contains(mover) || auditText(entry).contains(moverEmployee) || mover.equals(String.valueOf(entry.get("subjectId")))).map(entry -> String.valueOf(entry.get("entryId"))).findFirst()
                .orElseThrow(() -> new AssertionError("fixture: no grant audit entry about mover"));
    }

    @Test
    @DisplayName("#730 S3 契约第 12c 条与不变量 28：组织数据管理员凭一条授予审计条目标记已调整，200、ADJUSTED，库里调整人为本人并关联该审计条目，审计 PermissionAdjustmentAdjusted；安全审计员 403 条目不变")
    void anAdministratorMarksAnItemAdjusted() throws Exception {
        UUID item = openSensitive();
        String entry = anAuditEntryId();

        MockHttpServletResponse refused = commandWithCurrentVersion(session("auditor"), item, "mark-adjusted", adjustedBy("auditEntryId", entry));
        assertThat(refused.getStatus()).isEqualTo(403);
        assertUntouched(item);
        MockHttpServletResponse adjusted = commandWithCurrentVersion(session("oda"), item, "mark-adjusted", adjustedBy("auditEntryId", entry));

        assertThat(adjusted.getStatus()).as(adjusted.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(adjusted.getContentAsString(), "$.status")).isEqualTo("ADJUSTED");
        Map<String, Object> row = rowOf(item);
        assertThat(row).containsEntry("status", "ADJUSTED");
        assertThat(row.get("adjusted_by").toString()).isEqualTo(userId("oda"));
        assertThat(row.get("adjusted_audit_entry_id").toString()).isEqualTo(entry);
        eventually(() -> assertThat(auditEntries(ADJUSTED_ACTION)).anySatisfy(logged -> assertThat(logged)
                .containsEntry("subjectKind", "PERMISSION_ADJUSTMENT").containsEntry("subjectId", item.toString()).containsEntry("actorId", userId("oda"))));
    }

    @Test
    @DisplayName("#730 S3 不变量 28、契约第 12c 条：标记已调整必须恰好关联授权申请或授予审计条目其一——两个都不带、两个都带都是 400，条目不变")
    void markAdjustedNeedsExactlyOneReference() throws Exception {
        UUID item = openSensitive();
        String entry = anAuditEntryId();

        MockHttpServletResponse neither = commandWithCurrentVersion(session("oda"), item, "mark-adjusted", "{}");
        MockHttpServletResponse both = commandWithCurrentVersion(session("oda"), item, "mark-adjusted",
                "{\"authorizationRequestId\":\"" + UUID.randomUUID() + "\",\"auditEntryId\":\"" + entry + "\"}");

        assertThat(neither.getStatus()).isEqualTo(400);
        assertThat(both.getStatus()).isEqualTo(400);
        assertUntouched(item);
    }
}
