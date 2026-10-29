package com.forcitis.one.acceptance.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Names;
import com.forcitis.one.acceptance.P5Support;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P5 ADR-0033 section 7 items 1 and 2 (card acceptance item 3, 3.2 item 3): the audit trail subscribes to domain events and
 * writes the subject from the envelope: the kind is worked out from the aggregate type by a fixed rule, the object id is the envelope's
 * aggregateId. An aggregate type the rule does not know writes no entry, never a text one, and raises an ALERT.
 * <p>
 * The envelopes are built by hand around the real LegalEntityRegistered record, with the aggregate type and id of the case, so that
 * every aggregate of the card is covered without driving its whole use case; item 4 drives the real use cases.
 */
class AuditSubjectFromEventIT extends P5Support {

    static final String EVENT_TYPE = "organization.workforce.LegalEntityRegistered";

    static final TestDatabase postgres = TestPostgres.database(AuditSubjectFromEventIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private List<Map<String, Object>> entriesOf(Object envelope) throws Exception {
        Method eventId = envelope.getClass().getMethod("eventId");
        return auditRows("select " + P5Names.SUBJECT_KIND + ", " + P5Names.SUBJECT_ID + ", action from audit_entries where event_id = ?", eventId.invoke(envelope));
    }

    private void publishAndAwait(String aggregateType, UUID aggregateId, String expectedKind) throws Exception {
        Object envelope = envelopeFor(EVENT_TYPE, aggregateType, aggregateId);

        publishLegalEntityRegisteredWith(envelope);

        List<Map<String, Object>> written = Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> entriesOf(envelope), found -> !found.isEmpty());
        assertThat(written).as("entries of the event of a %s", aggregateType).hasSize(1);
        assertThat(written.getFirst().get(P5Names.SUBJECT_KIND)).as("kind of a %s", aggregateType).isEqualTo(expectedKind);
        assertThat(written.getFirst().get(P5Names.SUBJECT_ID)).as("object id of a %s", aggregateType).isEqualTo(aggregateId);
    }

    @Test
    @DisplayName("R-1b P5 审计订阅事件：条目的对象类别由事件聚合类型换算、对象 ID 等于信封 aggregateId——审批请求、工时单、角色、法人归属各一例")
    void theKindComesFromTheAggregateTypeAndTheIdFromTheEnvelope() throws Exception {
        publishAndAwait("ApprovalRequest", UUID.randomUUID(), P5Names.APPROVAL_REQUEST);
        publishAndAwait("Timesheet", UUID.randomUUID(), P5Names.TIMESHEET);
        publishAndAwait("Role", UUID.randomUUID(), P5Names.ROLE);
        publishAndAwait("LegalEntityAttribution", UUID.randomUUID(), P5Names.LEGAL_ENTITY_ATTRIBUTION);
        publishAndAwait("UserAccount", UUID.randomUUID(), P5Names.USER_ACCOUNT);
        publishAndAwait("LegalEntity", UUID.randomUUID(), P5Names.LEGAL_ENTITY);
    }

    @Test
    @DisplayName("R-1b P5 审计订阅事件：聚合类型无法换算时不写条目，失败并 ALERT（日志断言），不回退成「类型:ID」文本")
    void anAggregateTypeThatIsNotKnownWritesNothingAndRaisesAnAlert() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        Object envelope = envelopeFor(EVENT_TYPE, "NoSuchAggregate", aggregateId);
        int mark = logMark();

        publishLegalEntityRegisteredWith(envelope);

        List<String> alerts = Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> alertsSince(mark), found -> !found.isEmpty());
        assertThat(alerts).anyMatch(alert -> alert.contains("NoSuchAggregate") || alert.contains(aggregateId.toString()));
        assertThat(entriesOf(envelope)).as("no entry for an aggregate type the audit does not know").isEmpty();
        assertThat(auditRows("select count(*) as n from audit_entries where " + P5Names.SUBJECT_ID + " = ?", aggregateId).getFirst().get("n"))
                .as("not under any kind").isEqualTo(0L);
    }
}
