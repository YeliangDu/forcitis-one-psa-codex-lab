package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** ADR-0033 section 5 item 4 (U-3: four eyes) and section 2 item 1 (confirmation status and periods) through the HTTP contract. */
class SourceReferenceConfirmationIT extends SourceReferenceHttpSupport {

    static final TestDatabase postgres = TestPostgres.database(SourceReferenceConfirmationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    // RULE-463
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 4 条：核算组织编码登记为待确认；录入人即使持确认权限也不能自己确认（403），无确认权限 403，他人确认后生效")
    void fourEyes() throws Exception {
        UUID legalEntity = createLegalEntity("SRA", "Synthetic SRA");
        Registered row = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 1021, legalEntity, TODAY);
        assertThat(row(row.sourceReferenceId())).isEqualTo("PENDING 1021 " + legalEntity + " [" + TODAY + ", null)");

        MockHttpServletResponse bySelf = decide(RECORDER_WHO_ALSO_APPROVES, row, "confirm");
        assertThat(bySelf.getStatus()).as(bySelf.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(bySelf.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR");
        assertThat(row(row.sourceReferenceId())).startsWith("PENDING ");

        assertThat(decide(RECORDER_ONLY, row, "confirm").getStatus()).as("confirming needs source-reference:approve").isEqualTo(403);
        assertThat(row(row.sourceReferenceId())).startsWith("PENDING ");

        MockHttpServletResponse byAnother = decide(APPROVER, row, "confirm");
        assertThat(byAnother.getStatus()).as(byAnother.getContentAsString()).isBetween(200, 299);
        assertThat(row(row.sourceReferenceId())).isEqualTo("CONFIRMED 1021 " + legalEntity + " [" + TODAY + ", null)");
        assertThat(outboundAccountingCode(legalEntity, TODAY)).isEqualTo(1021L);
    }

    // RULE-464
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 2、4 条：从未确认过编码的法人，出站查不到即失败并告警（投递挂起）")
    void neverConfirmedMeansNoOutbound() throws Exception {
        UUID withoutCode = createLegalEntity("SRG", "Synthetic SRG");
        UUID onlyPending = createLegalEntity("SRH", "Synthetic SRH");
        registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 1031, onlyPending, TODAY);

        int mark = logMark();
        assertThat(catchThrowable(() -> outboundAccountingCode(withoutCode, TODAY))).as("no code at all").isNotNull();
        assertThat(catchThrowable(() -> outboundAccountingCode(onlyPending, TODAY))).as("a pending code is not used").isNotNull();
        assertThat(alertsSince(mark).stream().filter(line -> line.startsWith("ALERT platform.integration"))).hasSizeGreaterThanOrEqualTo(2);
    }

    // RULE-465
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条、第 5 项第 4 条：更正待确认期间出站仍用原已确认行；确认时同一事务关闭旧行并启用新行，两行的来源 ID 与自有 ID 都不改写")
    void correctionClosesTheOldRowOnConfirmation() throws Exception {
        UUID legalEntity = createLegalEntity("SRB", "Synthetic SRB");
        Registered first = confirmedAccountingCode(2101, legalEntity, TODAY);
        LocalDate later = TODAY.plusDays(14);
        setToday(later);

        Registered correction = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 2102, legalEntity, later);
        assertThat(outboundAccountingCode(legalEntity, later)).as("while the correction waits, the confirmed code stays in use").isEqualTo(2101L);

        MockHttpServletResponse confirmed = decide(APPROVER, correction, "confirm");
        assertThat(confirmed.getStatus()).as(confirmed.getContentAsString()).isBetween(200, 299);
        assertThat(row(first.sourceReferenceId())).isEqualTo("CONFIRMED 2101 " + legalEntity + " [" + TODAY + ", " + later + ")");
        assertThat(row(correction.sourceReferenceId())).isEqualTo("CONFIRMED 2102 " + legalEntity + " [" + later + ", null)");
        assertThat(outboundAccountingCode(legalEntity, later)).isEqualTo(2102L);
    }

    // RULE-467
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条、第 5 项第 4 条：拒绝的行置为已拒绝，不影响已确认行、出站与之后的登记")
    void rejectionLeavesEverythingElseAlone() throws Exception {
        UUID legalEntity = createLegalEntity("SRC", "Synthetic SRC");
        Registered first = confirmedAccountingCode(3101, legalEntity, TODAY);
        Registered wrong = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 3102, legalEntity, TODAY.plusDays(1));

        MockHttpServletResponse rejected = decide(APPROVER, wrong, "reject");
        assertThat(rejected.getStatus()).as(rejected.getContentAsString()).isBetween(200, 299);
        assertThat(row(wrong.sourceReferenceId())).startsWith("REJECTED 3102 ");
        assertThat(row(first.sourceReferenceId())).isEqualTo("CONFIRMED 3101 " + legalEntity + " [" + TODAY + ", null)");
        setToday(TODAY.plusDays(1));
        assertThat(outboundAccountingCode(legalEntity, TODAY.plusDays(1))).isEqualTo(3101L);

        Registered right = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 3103, legalEntity, TODAY.plusDays(1));
        assertThat(decide(APPROVER, right, "confirm").getStatus()).isBetween(200, 299);
        assertThat(outboundAccountingCode(legalEntity, TODAY.plusDays(1))).isEqualTo(3103L);
        assertThat(decide(APPROVER, wrong, "confirm").getStatus()).as("a rejected row is decided").isBetween(400, 499);
        assertThat(row(wrong.sourceReferenceId())).startsWith("REJECTED ");
    }

    // RULE-468
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条：编码已确认给另一法人时，待确认可以登记，确认失败（409）且不静默覆盖，行仍待确认")
    void confirmingATakenCodeFails() throws Exception {
        UUID holder = createLegalEntity("SRE", "Synthetic SRE");
        UUID other = createLegalEntity("SRF", "Synthetic SRF");
        Registered held = confirmedAccountingCode(4101, holder, TODAY);

        Registered clash = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 4101, other, TODAY);
        MockHttpServletResponse confirmed = decide(APPROVER, clash, "confirm");

        assertThat(confirmed.getStatus()).as(confirmed.getContentAsString()).isEqualTo(409);
        assertThat(row(clash.sourceReferenceId())).startsWith("PENDING 4101 ");
        assertThat(row(held.sourceReferenceId())).isEqualTo("CONFIRMED 4101 " + holder + " [" + TODAY + ", null)");
        assertThat(outboundAccountingCode(holder, TODAY)).isEqualTo(4101L);
    }

    // RULE-470
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 4 条：登记、确认、拒绝都写审计")
    void everyStepIsAudited() throws Exception {
        UUID legalEntity = createLegalEntity("SRJ", "Synthetic SRJ");

        int before = integrationAudit().size();
        Registered first = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 5101, legalEntity, TODAY);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(integrationAudit()).as("registration").hasSizeGreaterThan(before));

        int registered = integrationAudit().size();
        assertThat(decide(APPROVER, first, "confirm").getStatus()).isBetween(200, 299);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(integrationAudit()).as("confirmation").hasSizeGreaterThan(registered));

        int confirmed = integrationAudit().size();
        Registered second = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 5102, legalEntity, TODAY.plusDays(3));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(integrationAudit()).hasSizeGreaterThan(confirmed));
        int registeredAgain = integrationAudit().size();
        assertThat(decide(APPROVER, second, "reject").getStatus()).isBetween(200, 299);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(integrationAudit()).as("rejection").hasSizeGreaterThan(registeredAgain));
    }

    static final String CONFIRMED_ACTION = "platform.integration.SourceReferenceConfirmed";

    /** Every row of one legal entity as GET /api/v1/platform/source-references answers it, keyed by sourceReferenceId. */
    private Map<String, Object> listedRow(UUID legalEntity, Registered row) throws Exception {
        MockHttpServletResponse found = mvc.perform(get(SOURCE_REFERENCES).param("sourceSystem", ACCOUNTING_SYSTEM).param("sourceObjectKind", LEGAL_ENTITY)
                .param("ownId", legalEntity.toString()).with(APPROVER)).andReturn().getResponse();
        assertThat(found.getStatus()).as(found.getContentAsString()).isEqualTo(200);
        List<Map<String, Object>> items = JsonPath.read(found.getContentAsString(), "$.items[?(@.sourceReferenceId == '" + row.sourceReferenceId() + "')]");
        assertThat(items).as("row %s is listed for its legal entity", row.sourceReferenceId()).hasSize(1);
        return items.get(0);
    }

    // RULE-466
    @Test
    @DisplayName("R-1b P3a 复审 M2（ADR-0033 第 2 项第 1 条、第 5 项第 4 条，用户口径「允许同日更正」）：已确认编码 A 自 D 起生效，同日 D 生效的更正 B 由他人确认后，出站自 D 起取到 B；A 行仍在、区间为空 [D, D)、来源 ID 不改写，确认写审计")
    void aCorrectionFromTheSameDayReplacesAWrongCode() throws Exception {
        UUID legalEntity = createLegalEntity("SRL", "Synthetic SRL");
        LocalDate day = TODAY;
        Registered wrong = confirmedAccountingCode(9401, legalEntity, day);

        Registered correction = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 9402, legalEntity, day);
        assertThat(outboundAccountingCode(legalEntity, day)).as("while the correction waits, the confirmed code stays in use").isEqualTo(9401L);

        int confirmationsBefore = auditedActions(CONFIRMED_ACTION).size();
        MockHttpServletResponse confirmed = decide(APPROVER, correction, "confirm");
        assertThat(confirmed.getStatus()).as("a correction from the day the wrong code starts: " + confirmed.getContentAsString()).isBetween(200, 299);

        assertThat(row(correction.sourceReferenceId())).isEqualTo("CONFIRMED 9402 " + legalEntity + " [" + day + ", null)");
        assertThat(outboundAccountingCode(legalEntity, day)).as("on the day itself").isEqualTo(9402L);
        assertThat(outboundAccountingCode(legalEntity, day.plusDays(30))).as("and after it").isEqualTo(9402L);

        assertThat(row(wrong.sourceReferenceId())).as("the wrong row stays, same source id and own id, its period emptied")
                .endsWith(" 9401 " + legalEntity + " [" + day + ", " + day + ")");
        Map<String, Object> listed = listedRow(legalEntity, wrong);
        assertThat(listed).as("the wrong row as the legal entity page reads it").containsEntry("sourceId", "9401").containsEntry("ownId", legalEntity.toString())
                .containsEntry("effectiveFrom", day.toString()).containsEntry("effectiveTo", day.toString());
        assertThat(listedRow(legalEntity, correction)).containsEntry("sourceId", "9402").containsEntry("confirmationStatus", "CONFIRMED")
                .containsEntry("effectiveFrom", day.toString());
        assertThat(listedRow(legalEntity, correction).get("effectiveTo")).isNull();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(auditedActions(CONFIRMED_ACTION)).as("the confirmation is audited")
                .hasSizeGreaterThan(confirmationsBefore));
    }

    // RULE-463
    @Test
    @DisplayName("R-1b P3a 复审 M2（ADR-0033 第 5 项第 4 条，用户口径「允许同日更正」，同样四眼）：同日更正由录入人自己确认得 403，行仍待确认、出站仍用原编码；他人确认后才生效")
    void aSameDayCorrectionStillNeedsASecondPerson() throws Exception {
        UUID legalEntity = createLegalEntity("SRM", "Synthetic SRM");
        LocalDate day = TODAY;
        Registered wrong = confirmedAccountingCode(9501, legalEntity, day);
        Registered correction = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 9502, legalEntity, day);

        MockHttpServletResponse bySelf = decide(RECORDER_WHO_ALSO_APPROVES, correction, "confirm");
        assertThat(bySelf.getStatus()).as(bySelf.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(bySelf.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR");
        assertThat(row(correction.sourceReferenceId())).startsWith("PENDING 9502 ");
        assertThat(row(wrong.sourceReferenceId())).isEqualTo("CONFIRMED 9501 " + legalEntity + " [" + day + ", null)");
        assertThat(outboundAccountingCode(legalEntity, day)).isEqualTo(9501L);

        MockHttpServletResponse byAnother = decide(APPROVER, correction, "confirm");
        assertThat(byAnother.getStatus()).as(byAnother.getContentAsString()).isBetween(200, 299);
        assertThat(outboundAccountingCode(legalEntity, day)).isEqualTo(9502L);
        assertThat(row(wrong.sourceReferenceId())).endsWith(" 9501 " + legalEntity + " [" + day + ", " + day + ")");
    }

    // RULE-466
    @Test
    @DisplayName("R-1b P3a 复审 M2 回归（ADR-0033 第 2 项第 1 条）：生效日早于在用编码的更正仍被拒绝（4xx），在用编码不变；晚于的更正照旧关闭旧行并生效")
    void aCorrectionBeforeTheCodeStartsIsStillRefused() throws Exception {
        UUID legalEntity = createLegalEntity("SRN", "Synthetic SRN");
        LocalDate day = TODAY.plusDays(10);
        Registered inEffect = confirmedAccountingCode(9601, legalEntity, day);
        setToday(day);

        MockHttpServletResponse earlier = postJson(RECORDER_WHO_ALSO_APPROVES, body(ACCOUNTING_SYSTEM, LEGAL_ENTITY, "9602", legalEntity, day.minusDays(1)));
        if (earlier.getStatus() == 201) {
            Registered tooEarly = new Registered(UUID.fromString(JsonPath.read(earlier.getContentAsString(), "$.sourceReferenceId")), earlier.getHeader("ETag"));
            MockHttpServletResponse refused = decide(APPROVER, tooEarly, "confirm");
            assertThat(refused.getStatus()).as("a correction before the code in effect starts: " + refused.getContentAsString()).isBetween(400, 499);
            assertThat(row(tooEarly.sourceReferenceId())).startsWith("PENDING 9602 ");
        } else {
            assertThat(earlier.getStatus()).as("a correction before the code in effect starts: " + earlier.getContentAsString()).isBetween(400, 499);
        }
        assertThat(row(inEffect.sourceReferenceId())).isEqualTo("CONFIRMED 9601 " + legalEntity + " [" + day + ", null)");
        assertThat(outboundAccountingCode(legalEntity, day)).isEqualTo(9601L);

        LocalDate later = day.plusDays(5);
        Registered correction = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 9603, legalEntity, later);
        MockHttpServletResponse confirmed = decide(APPROVER, correction, "confirm");
        assertThat(confirmed.getStatus()).as(confirmed.getContentAsString()).isBetween(200, 299);
        assertThat(row(inEffect.sourceReferenceId())).isEqualTo("CONFIRMED 9601 " + legalEntity + " [" + day + ", " + later + ")");
        assertThat(row(correction.sourceReferenceId())).isEqualTo("CONFIRMED 9603 " + legalEntity + " [" + later + ", null)");
        assertThat(outboundAccountingCode(legalEntity, day)).isEqualTo(9601L);
        assertThat(outboundAccountingCode(legalEntity, later)).isEqualTo(9603L);
    }

    static final RequestPostProcessor SECOND_APPROVER = person(18, APPROVE);

    private int sessionsWaitingOnALock(Connection observer) throws Exception {
        try (PreparedStatement waiting = observer.prepareStatement("""
                select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and pid <> pg_backend_pid()
                """); ResultSet rs = waiting.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private long overlappingConfirmedRows(UUID legalEntity) throws Exception {
        try (PreparedStatement overlaps = integration.prepareStatement("""
                select count(*) from source_references a join source_references b
                    on a.own_id = b.own_id and a.source_system = b.source_system and a.source_object_kind = b.source_object_kind
                   and a.source_reference_id < b.source_reference_id
                 where a.own_id = ? and a.confirmation_status = 'CONFIRMED' and b.confirmation_status = 'CONFIRMED'
                   and daterange(a.effective_from_date, a.effective_to_date, '[)') && daterange(b.effective_from_date, b.effective_to_date, '[)')
                """)) {
            overlaps.setObject(1, legalEntity);
            try (ResultSet rs = overlaps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // RULE-468
    @Test
    @DisplayName("R-1b P3a 复审 L9（ADR-0033 第 2 项第 1 条）：同一法人两笔更正由两人并发确认——一笔成功、后者 409 且仍待确认；任一日期只有一行已确认")
    void concurrentCorrectionsConfirmOnlyOne() throws Exception {
        UUID legalEntity = createLegalEntity("SRK", "Synthetic SRK");
        Registered inEffect = confirmedAccountingCode(9301, legalEntity, TODAY);
        setToday(TODAY.plusDays(14));
        Registered sooner = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 9302, legalEntity, TODAY.plusDays(14));
        Registered later = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 9303, legalEntity, TODAY.plusDays(21));

        ExecutorService approvers = Executors.newFixedThreadPool(2);
        List<Future<MockHttpServletResponse>> decisions = new ArrayList<>();
        try (Connection holder = schemaConnection("platform_integration"); Connection observer = schemaConnection("platform_integration")) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("select 1 from source_references where source_reference_id = ? for update")) {
                lock.setObject(1, inEffect.sourceReferenceId());
                lock.executeQuery().close();
            }
            decisions.add(approvers.submit(() -> decide(APPROVER, sooner, "confirm")));
            decisions.add(approvers.submit(() -> decide(SECOND_APPROVER, later, "confirm")));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(
                    sessionsWaitingOnALock(observer) + decisions.stream().filter(Future::isDone).count())
                    .as("both confirmations have read the code in effect and wait for it, or are done").isGreaterThanOrEqualTo(2));
            holder.commit();
        }
        List<Integer> statuses = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        try {
            for (Future<MockHttpServletResponse> decision : decisions) {
                MockHttpServletResponse answer = decision.get(30, TimeUnit.SECONDS);
                statuses.add(answer.getStatus());
                bodies.add(answer.getContentAsString());
            }
        } finally {
            approvers.shutdownNow();
        }

        assertThat(statuses).as(bodies.toString()).anyMatch(status -> status / 100 == 2).contains(409);
        Registered winner = statuses.get(0) / 100 == 2 ? sooner : later;
        Registered loser = winner == sooner ? later : sooner;
        long winnerCode = winner == sooner ? 9302L : 9303L;
        LocalDate winnerFrom = winner == sooner ? TODAY.plusDays(14) : TODAY.plusDays(21);
        assertThat(row(loser.sourceReferenceId())).as("the confirmation that lost the race is refused, not applied").startsWith("PENDING ");
        assertThat(row(winner.sourceReferenceId())).isEqualTo("CONFIRMED " + winnerCode + " " + legalEntity + " [" + winnerFrom + ", null)");
        assertThat(row(inEffect.sourceReferenceId())).isEqualTo("CONFIRMED 9301 " + legalEntity + " [" + TODAY + ", " + winnerFrom + ")");
        assertThat(overlappingConfirmedRows(legalEntity)).as("confirmed rows of one legal entity never cover the same day").isZero();
        assertThat(outboundAccountingCode(legalEntity, TODAY.plusDays(21))).isEqualTo(winnerCode);
    }
}
