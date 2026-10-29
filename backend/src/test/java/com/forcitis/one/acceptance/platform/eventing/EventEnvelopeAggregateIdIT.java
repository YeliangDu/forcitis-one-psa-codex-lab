package com.forcitis.one.acceptance.platform.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.GetTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.Hours;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * R-1b P5 ADR-0033 section 7 item 1 and ADR-0039 section 5 item 1 (card acceptance items 4 and 5): the aggregateId of every event
 * envelope, and the id of each of its related aggregates, is a canonical UUID and is the primary key of the aggregate it names; the
 * envelope carries no source. One run: a timesheet is submitted and approved, a legal entity is registered, the work calendar is
 * maintained, an employee is attributed to a legal entity and the timesheet numbering rule is confirmed. The envelopes are read from the
 * event publication registry, the keys from the owning schemas.
 */
class EventEnvelopeAggregateIdIT extends P5Support {

    static final TestDatabase postgres = TestPostgres.database(EventEnvelopeAggregateIdIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    SubmitTimesheet submitTimesheet;

    @Autowired
    GetTimesheet getTimesheet;

    private SignedIn maintainer;
    private TimesheetId timesheet;
    private DocumentReference document;

    private void aTimesheetSubmittedAndApproved() throws Exception {
        UUID maintainerEmployee = newEmployeeId();
        employee(maintainerEmployee, unit, null);
        grantByOperations(maintainerEmployee, "org_data_admin");
        grantByOperations(maintainerEmployee, "business_admin");
        maintainer = signIn(maintainerEmployee);
        attributeEmployee(maintainer, initiator, legalEntity.value());
        confirmSeededNumberingRule("TIMESHEET", maintainer);
        timesheet = new TimesheetId(UUID.randomUUID());
        document = new DocumentReference(new DocumentTypeCode("TIMESHEET"), documentIdOf(UUID.fromString(timesheet.value().toString())));

        actInSession(initiatorAccount);
        submitTimesheet.execute(new SubmitTimesheetCommand(timesheet, TODAY.minusDays(3), Hours.of("8"), employeeIdOf(projectManager)));
        UUID task = Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> openTaskIds(document), found -> !found.isEmpty()).getFirst();
        actOk(task, projectManagerAccount, "claim", null);
        actOk(task, projectManagerAccount, "decide", "{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}");
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
            actInSession(initiatorAccount);
            return getTimesheet.execute(timesheet).status() == TimesheetStatus.APPROVED;
        });
    }

    private Set<UUID> keys(String schema, String sql) throws SQLException {
        Set<UUID> keys = new HashSet<>();
        for (Map<String, Object> row : query(schema, sql)) {
            keys.add((UUID) row.values().iterator().next());
        }
        return keys;
    }

    private static List<String> aggregateIdsOf(List<JsonNode> envelopes, String aggregateType) {
        return envelopes.stream().filter(envelope -> aggregateType.equals(envelope.path("aggregateType").asString())).map(envelope -> envelope.path("aggregateId").asString()).toList();
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 7 项第 1 条：事件信封的 aggregateId 与 relatedAggregates 的 ID 都是规范 UUID——提交工时到审批通过、法人归属维护、工作日历维护、编号规则确认走一遍")
    void everyAggregateIdIsACanonicalUuid() throws Exception {
        aTimesheetSubmittedAndApproved();

        List<JsonNode> envelopes = registeredEnvelopes();

        assertThat(envelopes).as("envelopes in the registry").hasSizeGreaterThan(10);
        List<String> wrong = new ArrayList<>();
        for (JsonNode envelope : envelopes) {
            checkUuid(wrong, envelope.path("eventType").asString() + " aggregateId", envelope.path("aggregateId"));
            for (JsonNode related : envelope.path("relatedAggregates")) {
                checkUuid(wrong, envelope.path("eventType").asString() + " related " + related.path("aggregateType").asString(), related.path("aggregateId"));
            }
        }
        assertThat(wrong).isEmpty();
    }

    private static void checkUuid(List<String> wrong, String what, JsonNode id) {
        String text = id.asString(null);
        if (text == null || !CANONICAL_UUID.matcher(text).matches()) {
            wrong.add(what + " = " + id);
        }
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 7 项第 1 条 / D-3：审批请求事件的 aggregateId 等于审批请求 ID（不含单据类型）；工时单事件等于工时单 ID；工作日历、法人归属、编号规则事件等于各自表的主键")
    void everyAggregateIdIsThePrimaryKeyOfItsAggregate() throws Exception {
        Set<String> before = new HashSet<>();
        registeredEnvelopes().forEach(envelope -> before.add(envelope.path("eventId").asString()));
        aTimesheetSubmittedAndApproved();
        List<JsonNode> envelopes = registeredEnvelopes();
        List<JsonNode> ofThisCase = envelopes.stream().filter(envelope -> !before.contains(envelope.path("eventId").asString())).toList();

        UUID request = requestIdOf(document);
        assertThat(aggregateIdsOf(registeredEnvelopes("ApprovalRoundStarted"), "ApprovalRequest")).as("the start of the approval round")
                .contains(request.toString());
        assertThat(aggregateIdsOf(ofThisCase, "ApprovalRequest")).as("approval request events published by this case").isNotEmpty().containsOnly(request.toString());
        assertThat(aggregateIdsOf(ofThisCase, "Timesheet")).as("timesheet events published by this case").isNotEmpty().containsOnly(timesheet.value().toString());

        assertThat(aggregateIdsOf(envelopes, "WorkCalendarYear")).as("work calendar events").isNotEmpty()
                .allSatisfy(id -> assertThat(keys("organization_attendance", "select id from work_calendar_years")).contains(UUID.fromString(id)));
        assertThat(aggregateIdsOf(envelopes, "LegalEntityAttribution")).as("legal entity attribution events").isNotEmpty()
                .allSatisfy(id -> assertThat(keys("organization_workforce", "select attribution_id from legal_entity_attributions")).contains(UUID.fromString(id)));
        assertThat(aggregateIdsOf(envelopes, "NumberingRule")).as("numbering rule events").isNotEmpty()
                .allSatisfy(id -> assertThat(keys("platform_numbering", "select numbering_rule_id from numbering_rules")).contains(UUID.fromString(id)));
        assertThat(aggregateIdsOf(envelopes, "LegalEntity")).as("legal entity events").isNotEmpty()
                .allSatisfy(id -> assertThat(keys("organization_workforce", "select id from legal_entities")).contains(UUID.fromString(id)));
    }

    @Test
    @DisplayName("R-1b P5 ADR-0039 第 5 条第 1 款：序列化后的事件信封 JSON 没有 source 键；工时回写照常发生（审批通过后工时单为 APPROVED）")
    void theEnvelopeHasNoSource() throws Exception {
        aTimesheetSubmittedAndApproved();

        List<JsonNode> envelopes = registeredEnvelopes();

        assertThat(envelopes).isNotEmpty();
        assertThat(envelopes).allSatisfy(envelope -> assertThat(envelope.has("source")).as(envelope.path("eventType").asString() + " has source").isFalse());
        for (JsonNode envelope : envelopes) {
            assertThat(envelope.path("actorId").asString()).as("actorId of " + envelope.path("eventType").asString()).matches(CANONICAL_UUID);
        }
    }
}
