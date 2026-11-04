package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Review M4 of PR #139: the manual source references can be read, so that an approver finds the {@code sourceReferenceId} to decide and the
 * legal entity page shows its accounting organization code (ADR-0033 section 5 items 3 and 4).
 * <p>
 * Contract: {@code GET /api/v1/platform/source-references} answers the list shape of every endpoint, {@code {items, nextCursor}}, each item
 * the same {@code SourceReferenceResponse} as the registration ({@code sourceReferenceId, sourceSystem, sourceObjectKind, sourceId} as a
 * decimal string{@code , ownId, confirmationStatus, effectiveFrom, effectiveTo}). Query parameters:
 * <ul>
 *   <li>{@code sourceSystem} and {@code sourceObjectKind}, required; only (ACCOUNTING_SYSTEM, LEGAL_ENTITY), the one category edited by
 *       hand, can be listed: any other is 422 {@code PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED}, so the read does not open the
 *       synchronised identifiers that the write keeps closed;</li>
 *   <li>{@code confirmationStatus}, optional filter ({@code PENDING} is the approver's to-do list);</li>
 *   <li>{@code ownId}, optional filter: every row of one legal entity, confirmed, pending and rejected, closed periods included; the
 *       code in effect is the confirmed row whose period holds the day;</li>
 *   <li>{@code limit} and {@code cursor}, the cursor paging of every list.</li>
 * </ul>
 * Permission: {@code platform.integration:source-reference:update} or {@code :approve}, either one (the maintainer sees what was
 * registered, the approver what waits; same pattern as the held sync runs open to the separation-hold approver). No session 401,
 * neither permission 403.
 */
class SourceReferenceQueryIT extends SourceReferenceHttpSupport {

    static final TestDatabase postgres = TestPostgres.database(SourceReferenceQueryIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final RequestPostProcessor SECOND_APPROVER = person(16, APPROVE);
    static final RequestPostProcessor LEGAL_ENTITY_MAINTAINER_ONLY = person(17, Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE,
            Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);

    MockHttpServletResponse list(RequestPostProcessor who, Map<String, String> parameters) throws Exception {
        MockHttpServletRequestBuilder request = get(SOURCE_REFERENCES);
        parameters.forEach(request::param);
        if (who != null) {
            request.with(who);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    static Map<String, String> accountingCodes(String... more) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("sourceSystem", ACCOUNTING_SYSTEM);
        parameters.put("sourceObjectKind", LEGAL_ENTITY);
        for (int i = 0; i < more.length; i += 2) {
            parameters.put(more[i], more[i + 1]);
        }
        return parameters;
    }

    static List<String> ids(MockHttpServletResponse answer) throws Exception {
        return JsonPath.read(answer.getContentAsString(), "$.items[*].sourceReferenceId");
    }

    /** sourceId → "status [from, to)" of every item, the way an approver or the legal entity page would read them. */
    static Map<String, String> byCode(MockHttpServletResponse answer) throws Exception {
        List<Map<String, Object>> items = JsonPath.read(answer.getContentAsString(), "$.items[*]");
        Map<String, String> rows = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            rows.put((String) item.get("sourceId"), item.get("confirmationStatus") + " [" + item.get("effectiveFrom") + ", " + item.get("effectiveTo") + ")");
        }
        return rows;
    }

    // RULE-471
    @Test
    @DisplayName("R-1b P3a 复审 M4（ADR-0033 第 5 项第 4 条）：审批人从待确认列表取得 sourceReferenceId 并据此确认；列表只含待确认行，可按游标翻页")
    void theApproverFindsWhatWaits() throws Exception {
        UUID first = createLegalEntity("SQA", "Synthetic SQA");
        UUID second = createLegalEntity("SQB", "Synthetic SQB");
        Registered confirmed = confirmedAccountingCode(7101, first, TODAY);
        Registered correction = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 7102, first, TODAY.plusDays(7));
        Registered rejected = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 7103, first, TODAY.plusDays(9));
        assertThat(decide(APPROVER, rejected, "reject").getStatus()).isBetween(200, 299);
        Registered waiting = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 7201, second, TODAY);

        MockHttpServletResponse pending = list(APPROVER, accountingCodes("confirmationStatus", "PENDING"));
        assertThat(pending.getStatus()).as(pending.getContentAsString()).isEqualTo(200);
        assertThat(ids(pending)).contains(correction.sourceReferenceId().toString(), waiting.sourceReferenceId().toString())
                .doesNotContain(confirmed.sourceReferenceId().toString(), rejected.sourceReferenceId().toString());
        List<String> statuses = JsonPath.read(pending.getContentAsString(), "$.items[*].confirmationStatus");
        assertThat(statuses).containsOnly("PENDING");
        List<Map<String, Object>> item = JsonPath.read(pending.getContentAsString(),
                "$.items[?(@.sourceReferenceId == '" + correction.sourceReferenceId() + "')]");
        assertThat(item).singleElement().satisfies(found -> {
            assertThat(found).containsEntry("sourceSystem", ACCOUNTING_SYSTEM).containsEntry("sourceObjectKind", LEGAL_ENTITY)
                    .containsEntry("sourceId", "7102").containsEntry("ownId", first.toString()).containsEntry("effectiveFrom", TODAY.plusDays(7).toString());
            assertThat(found.get("effectiveTo")).isNull();
        });

        List<String> paged = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            MockHttpServletResponse one = list(APPROVER, cursor == null ? accountingCodes("confirmationStatus", "PENDING", "limit", "1")
                    : accountingCodes("confirmationStatus", "PENDING", "limit", "1", "cursor", cursor));
            assertThat(one.getStatus()).as(one.getContentAsString()).isEqualTo(200);
            assertThat(ids(one)).hasSizeLessThanOrEqualTo(1);
            paged.addAll(ids(one));
            cursor = JsonPath.read(one.getContentAsString(), "$.nextCursor");
            if (cursor == null) {
                break;
            }
        }
        assertThat(paged).as("one row per page, following nextCursor").containsExactlyInAnyOrderElementsOf(ids(pending));

        List<String> listedFor7201 = JsonPath.read(pending.getContentAsString(), "$.items[?(@.sourceId == '7201')].sourceReferenceId");
        assertThat(listedFor7201).hasSize(1);
        String listed = listedFor7201.get(0);
        MockHttpServletResponse decided = decide(SECOND_APPROVER, new Registered(UUID.fromString(listed), null), "confirm");
        assertThat(decided.getStatus()).as(decided.getContentAsString()).isBetween(200, 299);
        assertThat(ids(list(APPROVER, accountingCodes("confirmationStatus", "PENDING")))).doesNotContain(listed);
    }

    // RULE-461
    @Test
    @DisplayName("R-1b P3a 复审 M4（ADR-0033 第 5 项第 3、4 条）：待确认列表需 source-reference:approve 或 :update 之一——未登录 401，只有法人维护权限 403")
    void listingNeedsUpdateOrApprove() throws Exception {
        UUID legalEntity = createLegalEntity("SQC", "Synthetic SQC");
        Registered waiting = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 7301, legalEntity, TODAY);
        Map<String, String> pending = accountingCodes("confirmationStatus", "PENDING");

        assertThat(list(null, pending).getStatus()).isEqualTo(401);
        assertThat(list(LEGAL_ENTITY_MAINTAINER_ONLY, pending).getStatus()).as("maintaining legal entities is not reading their codes").isEqualTo(403);
        MockHttpServletResponse byApprover = list(APPROVER, pending);
        assertThat(byApprover.getStatus()).as(byApprover.getContentAsString()).isEqualTo(200);
        assertThat(ids(byApprover)).contains(waiting.sourceReferenceId().toString());
        MockHttpServletResponse byRecorder = list(RECORDER_ONLY, pending);
        assertThat(byRecorder.getStatus()).as(byRecorder.getContentAsString()).isEqualTo(200);
        assertThat(ids(byRecorder)).contains(waiting.sourceReferenceId().toString());
    }

    // RULE-471
    @Test
    @DisplayName("R-1b P3a 复审 M4（ADR-0033 第 5 项第 3 条）：按法人自有 ID 查到当前已确认编码与全部历史行（含已关闭、待确认、已拒绝），不含其他法人；未登录 401，无权限 403")
    void oneLegalEntityShowsItsCurrentCodeAndHistory() throws Exception {
        UUID legalEntity = createLegalEntity("SQD", "Synthetic SQD");
        UUID other = createLegalEntity("SQE", "Synthetic SQE");
        LocalDate corrected = TODAY.plusDays(14);
        confirmedAccountingCode(8101, legalEntity, TODAY);
        confirmedAccountingCode(8201, other, TODAY);
        setToday(corrected);
        confirmedAccountingCode(8102, legalEntity, corrected);
        registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 8103, legalEntity, corrected.plusDays(16));
        Registered wrong = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 8104, legalEntity, corrected.plusDays(1));
        assertThat(decide(APPROVER, wrong, "reject").getStatus()).isBetween(200, 299);
        Map<String, String> ofTheLegalEntity = accountingCodes("ownId", legalEntity.toString());

        MockHttpServletResponse found = list(RECORDER_ONLY, ofTheLegalEntity);
        assertThat(found.getStatus()).as(found.getContentAsString()).isEqualTo(200);
        assertThat(byCode(found)).containsOnly(
                Map.entry("8101", "CONFIRMED [" + TODAY + ", " + corrected + ")"),
                Map.entry("8102", "CONFIRMED [" + corrected + ", null)"),
                Map.entry("8103", "PENDING [" + corrected.plusDays(16) + ", null)"),
                Map.entry("8104", "REJECTED [" + corrected.plusDays(1) + ", null)"));
        List<String> owners = JsonPath.read(found.getContentAsString(), "$.items[*].ownId");
        assertThat(owners).containsOnly(legalEntity.toString());
        assertThat(byCode(found).entrySet().stream().filter(row -> row.getValue().equals("CONFIRMED [" + corrected + ", null)")).map(Map.Entry::getKey))
                .as("the code in effect today").containsExactly("8102");
        assertThat(outboundAccountingCode(legalEntity, corrected)).as("the page shows what the outbound side uses").isEqualTo(8102L);

        assertThat(list(APPROVER, ofTheLegalEntity).getStatus()).isEqualTo(200);
        assertThat(list(null, ofTheLegalEntity).getStatus()).isEqualTo(401);
        assertThat(list(LEGAL_ENTITY_MAINTAINER_ONLY, ofTheLegalEntity).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("R-1b P3a 复审 M4（ADR-0033 第 5 项第 4 条）：读接口只开放人工登记的（核算系统, LEGAL_ENTITY）类别；同步写入的类别 422，缺类别 4xx")
    void onlyTheManualCategoryIsListed() throws Exception {
        for (String[] category : List.of(new String[] {"ORG_SOURCE", "EMPLOYEE"}, new String[] {"LEGACY_SYSTEM", "ORG_UNIT"},
                new String[] {"ACCOUNTING_SYSTEM", "EMPLOYEE"})) {
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("sourceSystem", category[0]);
            parameters.put("sourceObjectKind", category[1]);
            MockHttpServletResponse answer = list(RECORDER_WHO_ALSO_APPROVES, parameters);
            assertThat(answer.getStatus()).as("%s / %s: %s", category[0], category[1], answer.getContentAsString()).isEqualTo(422);
            assertThat((String) JsonPath.read(answer.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED");
        }
        assertThat(list(RECORDER_WHO_ALSO_APPROVES, Map.of("confirmationStatus", "PENDING")).getStatus()).as("no category given").isBetween(400, 499);
    }
}
