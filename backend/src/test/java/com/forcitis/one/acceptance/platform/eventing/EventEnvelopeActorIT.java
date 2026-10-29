package com.forcitis.one.acceptance.platform.eventing;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * R-1b P2 (ADR-0033 section 7 item 1, section 3 item 3 "事件信封 actorId 用用户 ID"; NEXT_BATCH_PLAN 7.2 C5): the envelope's actorId is a
 * UUID. A signed-in person acts as USER with their account id, which is not their employee id. A job or an event handler acts as SYSTEM;
 * its actorId is a UUID that stands for that job: the same for every event of the job, never its name, never the id of an account. The
 * audit trail still says which job it was. The envelopes are read as the event publication registry keeps them (platform_integration).
 */
class EventEnvelopeActorIT extends AccountsAcceptanceSupport {

    static final String ACCOUNT_PROVISIONING = "platform.identity.account-provisioning";
    static final JsonMapper JSON = JsonMapper.builder().build();

    static final TestDatabase postgres = TestPostgres.database(EventEnvelopeActorIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** The envelopes of the event type registered for the aggregate, one per event id, waiting up to 15 s for the first. */
    private List<JsonNode> envelopes(String eventType, String aggregateId) {
        return Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> registered(eventType, aggregateId), found -> !found.isEmpty());
    }

    private List<JsonNode> registered(String eventType, String aggregateId) throws Exception {
        Map<String, JsonNode> byEventId = new LinkedHashMap<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement rows = registry.prepareStatement("select serialized_event from " + table + " where event_type like ?")) {
                    rows.setString(1, "%." + eventType);
                    try (ResultSet result = rows.executeQuery()) {
                        while (result.next()) {
                            JsonNode envelope = JSON.readTree(result.getString(1)).path("envelope");
                            if (aggregateId.equals(envelope.path("aggregateId").asString(null))) {
                                byEventId.put(envelope.path("eventId").asString(), envelope);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(byEventId.values());
    }

    private boolean isAnAccount(String userId) throws Exception {
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement select = identity.prepareStatement("select count(*) from user_accounts where user_id = ?")) {
            select.setObject(1, UUID.fromString(userId));
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 7 项第 1 条 / 第 3 项第 3 条：用户操作的事件信封 actorType = USER，actorId 是该账号的 uuid 字符串（规范小写），不等于其员工 ID；审计条目的操作人同为该 uuid")
    void aSignedInPersonActsWithTheirAccountId() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        grantByOperations(person, "org_data_admin");
        SignedIn signedIn = signIn(person);
        UUID account = signedIn.userId();
        MockHttpServletResponse created = mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(session(signedIn))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"EVA\",\"name\":\"Synthetic envelope actor\"}")).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String legalEntityId = JsonPath.read(created.getContentAsString(), "$.legalEntityId");

        List<JsonNode> envelopes = envelopes("LegalEntityRegistered", legalEntityId);

        assertThat(envelopes).hasSize(1);
        JsonNode envelope = envelopes.getFirst();
        assertThat(envelope.path("actorType").asString()).isEqualTo("USER");
        assertThat(envelope.path("actorId").isString()).as("actorId is a JSON string").isTrue();
        assertThat(canonicalUuid(envelope.path("actorId").asString(), "the envelope actorId")).isEqualTo(account);
        assertThat(envelope.path("actorId").asString()).isNotEqualTo(text(person));
        assertThat(auditEntriesBy(account, "organization.workforce.LegalEntityRegistered")).extracting(entry -> entry.get("actorId"))
                .containsOnly(account.toString());
    }

    @Test
    @DisplayName("R-1b-P2-C5 系统作业的事件：actorType = SYSTEM，actorId 是代表该作业的 uuid（规范小写，不是作业名、不是任何账号的 user_id），同一作业的各事件 actorId 相同；审计条目操作人为空，说明仍写出作业名")
    void aJobActsAsSystemWithAStableUuid() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID first = newEmployeeId();
        UUID second = newEmployeeId();
        employee(first, unit, null);
        employee(second, unit, null);
        UUID firstAccount = signIn(first).userId();
        UUID secondAccount = signIn(second).userId();

        JsonNode opened = single(envelopes("AccountOpened", firstAccount.toString()));
        JsonNode openedToo = single(envelopes("AccountOpened", secondAccount.toString()));

        assertThat(opened.path("actorType").asString()).isEqualTo("SYSTEM");
        String actorId = opened.path("actorId").asString(null);
        assertThat(actorId).as("actorId of a job").isNotEqualTo(ACCOUNT_PROVISIONING);
        canonicalUuid(actorId, "the envelope actorId of a job");
        assertThat(isAnAccount(actorId)).as("the job's actorId is not the id of an account").isFalse();
        assertThat(openedToo.path("actorType").asString()).isEqualTo("SYSTEM");
        assertThat(openedToo.path("actorId").asString(null)).as("the same job has the same actorId in every event").isEqualTo(actorId);

        List<Map<String, Object>> audited = Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> auditQuery(null, null, "platform.identity.AccountOpened").stream()
                        .filter(entry -> firstAccount.toString().equals(entry.get("subjectId"))).toList(), found -> !found.isEmpty());
        assertThat(audited).allSatisfy(entry -> {
            assertThat(entry.get("actorId")).as("a job has no account as the audit actor").isNull();
            assertThat(String.valueOf(entry.get("detail"))).as("the audit trail still names the job").contains(ACCOUNT_PROVISIONING);
        });
    }

    private static JsonNode single(List<JsonNode> envelopes) {
        assertThat(envelopes).hasSize(1);
        return envelopes.getFirst();
    }
}
