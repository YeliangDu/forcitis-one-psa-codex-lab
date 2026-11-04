package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.attendance.api.StandardHoursVersionId;
import com.forcitis.one.organization.attendance.events.StandardHoursVersionPublished;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Card 1310: successful publication emits exactly one StandardHoursVersionPublished with the version ID,
 * effective date and envelope aggregate ID/type, USER publisher, correlation ID, business date and legal entity.
 * Invalid and overlapping publications emit none; voiding emits none and a subsequent publication has its own event.
 * DEFAULT carries a null envelope legal entity and is published only by one test in this class's database.
 * The analytics domain event is observed through ApplicationEvents, separately from publication audit entries.
 * Fixtures use synthetic legal entities and public HTTP, with no SQL cleanup or test execution order dependency.
 */
@RecordApplicationEvents
class StandardHoursPublishedEventIT extends AcceptanceSupport {

    /** Version dates follow the real Asia/Shanghai date because the database triggers use the real clock. */
    private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));

    static final TestDatabase postgres = TestPostgres.database(StandardHoursPublishedEventIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String VERSIONS = "/api/v1/organization/standard-hours-versions";
    private static final String PUBLISH = "organization.attendance:standard-hours:publish";
    private static final int CONFIRMER = 31;
    private static final RequestPostProcessor PUBLISHER = person(CONFIRMER, PUBLISH);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void useShanghaiToday() {
        setToday(TODAY);
    }

    @Test
    @DisplayName("1310 法人范围发布恰好产生一个事件，版本、生效日期与信封事实完整")
    void entityPublicationEmitsOneEventWithPublisherAndScope() throws Exception {
        UUID scope = newScope();
        LocalDate effectiveDate = TODAY.plusDays(10);

        String id = publish(publication(scope, effectiveDate, 450, 390), 1);

        assertPublishedEvent(id, effectiveDate, scope, 450, 390, 1);
    }

    @Test
    @DisplayName("1310 零分钟返回400、重叠日期返回409，拒绝发布均不产生发布事件")
    void refusedPublicationsEmitNoPublishedEvent() throws Exception {
        UUID scope = newScope();
        LocalDate effectiveDate = TODAY.plusDays(10);
        Map<String, Object> invalid = publication(scope, effectiveDate);
        invalid.put("workdayMinutes", 0);

        assertStatus(postPublication(invalid, PUBLISHER), 400, "zero workday minutes refuses publication");
        assertThat(events.stream(StandardHoursVersionPublished.class))
                .as("invalid publication emits no StandardHoursVersionPublished").isEmpty();

        String id = publish(publication(scope, effectiveDate), 1);
        assertPublishedEvent(id, effectiveDate, scope, 480, 420, 1);
        long beforeOverlap = events.stream(StandardHoursVersionPublished.class).count();

        assertStatus(postPublication(publication(scope, effectiveDate), PUBLISHER), 409,
                "an effective date equal to the latest version refuses publication");
        assertThat(events.stream(StandardHoursVersionPublished.class).count())
                .as("overlap refusal adds no StandardHoursVersionPublished for any version").isEqualTo(beforeOverlap);
        assertPublishedEvent(id, effectiveDate, scope, 480, 420, 1);
    }

    @Test
    @DisplayName("1310 作废不重复产生发布事件，同范围第二次发布携带自己的聚合标识和生效日期")
    void voidEmitsNoPublishedEventAndSuccessorEmitsItsOwn() throws Exception {
        UUID scope = newScope();
        LocalDate firstDate = TODAY.plusDays(10);
        String firstId = publish(publication(scope, firstDate), 1);
        assertPublishedEvent(firstId, firstDate, scope, 480, 420, 1);
        long beforeVoid = events.stream(StandardHoursVersionPublished.class).count();

        assertStatus(voidVersion(firstId, Map.of("reason", "Synthetic policy correction"), PUBLISHER), 200,
                "publisher can void the latest future version");

        assertThat(events.stream(StandardHoursVersionPublished.class).count())
                .as("void emits no StandardHoursVersionPublished for any version").isEqualTo(beforeVoid);
        assertPublishedEvent(firstId, firstDate, scope, 480, 420, 1);

        LocalDate secondDate = TODAY.plusDays(20);
        String secondId = publish(publication(scope, secondDate, 360, 330), 2);

        assertThat(secondId).as("second publication has its own version identifier").isNotEqualTo(firstId);
        assertPublishedEvent(secondId, secondDate, scope, 360, 330, 2);
        assertPublishedEvent(firstId, firstDate, scope, 480, 420, 1);
        assertThat(events.stream(StandardHoursVersionPublished.class).count())
                .as("two publications and one void produce exactly two publication events").isEqualTo(2L);
    }

    @Test
    @DisplayName("1310 唯一默认范围版本发布一个事件，信封法人为空且发布者与业务日期完整")
    void defaultPublicationEmitsOneEventWithoutLegalEntity() throws Exception {
        LocalDate effectiveDate = TODAY.minusDays(10);

        String id = publish(publication(null, effectiveDate, 465, 405), 1);

        assertPublishedEvent(id, effectiveDate, null, 465, 405, 1);
    }

    private void assertPublishedEvent(String id, LocalDate effectiveDate, UUID scope, int workdayMinutes, int adjustedWorkdayMinutes,
            int versionNumber) {
        StandardHoursVersionId versionId = new StandardHoursVersionId(UUID.fromString(id));
        assertThat(events.stream(StandardHoursVersionPublished.class)
                .filter(event -> versionId.equals(event.standardHoursVersionId())))
                .as("version " + id + " emits exactly one StandardHoursVersionPublished").singleElement().satisfies(event -> {
                    assertThat(event.standardHoursVersionId()).as("event identifies the published version").isEqualTo(versionId);
                    assertThat(event.standardHoursVersionId().value()).as("event version UUID equals the HTTP response identifier")
                            .isEqualTo(UUID.fromString(id));
                    assertThat(event.effectiveFromDate()).as("event carries the published effective start date").isEqualTo(effectiveDate);
                    EventEnvelope envelope = event.envelope();
                    assertThat(envelope).as("publication event has an envelope").isNotNull();
                    assertThat(envelope.aggregateId()).as("envelope aggregate ID is the published version UUID").isEqualTo(versionId.value());
                    assertThat(envelope.aggregateType()).as("envelope aggregate type is non-blank text").isNotBlank();
                    assertThat(envelope.actorType()).as("publication actor is a USER").isEqualTo(ActorType.USER);
                    assertThat(envelope.actorId()).as("publication actor is the signed-in publisher UUID")
                            .isEqualTo(UUID.fromString(principalName(CONFIRMER)));
                    assertThat(envelope.correlationId()).as("publication has a correlation ID").isNotNull();
                    assertThat(envelope.businessDate()).as("business date is the published effective start date").isEqualTo(effectiveDate);
                    assertThat(component(event, "scopeKind")).as("payload scope kind").hasToString(scope == null ? "DEFAULT" : "LEGAL_ENTITY");
                    assertThat(component(event, "legalEntityId")).as("payload legal entity id (null for the DEFAULT scope)")
                            .isEqualTo(scope == null ? null : new LegalEntityId(scope));
                    assertThat(component(event, "workdayMinutes")).as("payload workday minutes").isEqualTo(workdayMinutes);
                    assertThat(component(event, "adjustedWorkdayMinutes")).as("payload adjusted workday minutes").isEqualTo(adjustedWorkdayMinutes);
                    assertThat(component(event, "versionNumber")).as("payload per-scope version number").isEqualTo(versionNumber);
                    if (scope == null) {
                        assertThat(envelope.legalEntityId()).as("DEFAULT event has no legal entity").isNull();
                    } else {
                        assertThat(envelope.legalEntityId()).as("entity event carries the body's legal entity")
                                .isEqualTo(new LegalEntityId(scope));
                    }
                });
    }

    /** Reads a record component by name so a missing payload field is a red assertion, not a compile error for the whole module. */
    private static Object component(StandardHoursVersionPublished event, String name) {
        for (java.lang.reflect.RecordComponent candidate : StandardHoursVersionPublished.class.getRecordComponents()) {
            if (candidate.getName().equals(name)) {
                try {
                    return candidate.getAccessor().invoke(event);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError("payload component " + name + " is not readable", failure);
                }
            }
        }
        throw new AssertionError("StandardHoursVersionPublished has no payload component " + name);
    }

    private UUID newScope() throws Exception {
        return createLegalEntity(newEntityCode(), "Synthetic card 1310 entity");
    }

    /** A legal entity code is 2 to 10 upper-case letters: SH and eight random letters. */
    private static String newEntityCode() {
        StringBuilder code = new StringBuilder("SH");
        for (int i = 0; i < 8; i++) {
            code.append((char) ('A' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
        }
        return code.toString();
    }

    private static Map<String, Object> publication(UUID scope, LocalDate effectiveDate, int workdayMinutes, int adjustedWorkdayMinutes) {
        Map<String, Object> body = publication(scope, effectiveDate);
        body.put("workdayMinutes", workdayMinutes);
        body.put("adjustedWorkdayMinutes", adjustedWorkdayMinutes);
        return body;
    }

    private static Map<String, Object> publication(UUID scope, LocalDate effectiveDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (scope != null) {
            body.put("legalEntityId", scope.toString());
        }
        body.put("effectiveFromDate", effectiveDate.toString());
        body.put("workdayMinutes", 480);
        body.put("adjustedWorkdayMinutes", 420);
        body.put("basisRef", "Synthetic card 1310 policy");
        return body;
    }

    private MockHttpServletResponse postPublication(Map<String, Object> body, RequestPostProcessor caller) throws Exception {
        return mvc.perform(post(VERSIONS).with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn().getResponse();
    }

    private String publish(Map<String, Object> body, int expectedNumber) throws Exception {
        MockHttpServletResponse response = postPublication(body, PUBLISHER);
        assertStatus(response, 200, "valid publication returns 200");
        String content = response.getContentAsString();
        String id = JsonPath.read(content, "$.standardHoursVersionId");
        assertThat(id).as("publication returns a UUID standardHoursVersionId")
                .matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        assertThat((Integer) JsonPath.read(content, "$.versionNumber")).as("publication returns its per-scope version number")
                .isEqualTo(expectedNumber);
        return id;
    }

    private MockHttpServletResponse voidVersion(String id, Map<String, Object> body, RequestPostProcessor caller) throws Exception {
        return mvc.perform(post(VERSIONS + "/" + id + ":void").with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn().getResponse();
    }

    private static void assertStatus(MockHttpServletResponse response, int expected, String rule) throws Exception {
        assertThat(response.getStatus()).as(rule + "; response: " + response.getContentAsString()).isEqualTo(expected);
    }
}
