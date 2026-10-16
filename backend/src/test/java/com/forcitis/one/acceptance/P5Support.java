package com.forcitis.one.acceptance;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ground of the R-1b P5 acceptance tests that need the people and accounts of the approval tests plus: the envelopes the event
 * publication registry keeps, an envelope built without naming the types that P5 changes (aggregateId is a UUID, "source" is gone), a
 * domain event published by hand, and audit rows written with the final subject columns.
 */
public abstract class P5Support extends ApprovalP4Support {

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String SYNTHETIC_JOB = "organization.workforce.snapshot.sync";

    @Autowired
    protected ApplicationEventPublisher publisher;

    @Autowired
    protected TransactionTemplate transaction;

    /** Every envelope in the registry (open and completed publications), once per event id. */
    protected List<JsonNode> registeredEnvelopes() throws Exception {
        Map<String, JsonNode> byEventId = new LinkedHashMap<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement rows = registry.prepareStatement("select serialized_event from " + table);
                     ResultSet result = rows.executeQuery()) {
                    while (result.next()) {
                        JsonNode envelope = JSON.readTree(result.getString(1)).path("envelope");
                        if (envelope.has("eventId")) {
                            byEventId.put(envelope.path("eventId").asString(), envelope);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(byEventId.values());
    }

    protected List<JsonNode> registeredEnvelopes(String eventTypeSuffix) throws Exception {
        return registeredEnvelopes().stream().filter(envelope -> envelope.path("eventType").asString("").endsWith(eventTypeSuffix)).toList();
    }

    /**
     * An envelope of a SYSTEM actor for the given aggregate, filled by the type of each component, so that it is the same code before
     * and after P5 (aggregateId text or UUID; the source component only while it exists).
     */
    protected static Object envelopeFor(String eventType, String aggregateType, UUID aggregateId) {
        try {
            Class<?> type = Class.forName("com.forcitis.one.kernel.event.EventEnvelope");
            RecordComponent[] components = type.getRecordComponents();
            Object[] values = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                UUID job = SystemActors.idOf(SYNTHETIC_JOB);
                values[i] = switch (components[i].getName()) {
                    case "eventId", "correlationId" -> UUID.randomUUID();
                    case "eventType" -> eventType;
                    case "schemaVersion" -> 1;
                    case "occurredAt" -> NOW;
                    case "aggregateType" -> aggregateType;
                    case "aggregateId" -> types[i] == UUID.class ? aggregateId : aggregateId.toString();
                    case "aggregateVersion" -> 1L;
                    case "actorType" -> enumValue(types[i], "SYSTEM");
                    case "actorId" -> types[i] == UUID.class ? job : job.toString();
                    case "relatedAggregates" -> List.of();
                    case "source" -> oldSource();
                    default -> null;
                };
            }
            Constructor<?> constructor = type.getDeclaredConstructor(types);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<Enum>) type, name);
    }

    private static Object oldSource() throws ReflectiveOperationException {
        Class<?> system = Class.forName("com.forcitis.one.kernel.event.SourceSystem");
        Class<?> source = Class.forName("com.forcitis.one.kernel.event.EventSource");
        return source.getDeclaredConstructors()[0].newInstance(enumValue(system, "ONE"), null);
    }

    /** Publishes the real LegalEntityRegistered event record with this envelope, in a transaction, as an aggregate would. */
    protected void publishLegalEntityRegisteredWith(Object envelope) throws ReflectiveOperationException {
        Class<?> event = Class.forName("com.forcitis.one.organization.workforce.events.LegalEntityRegistered");
        Object legalEntity = new LegalEntityId(UUID.randomUUID());
        Object code = new LegalEntityCode("PFA");
        Object record = event.getDeclaredConstructors()[0].newInstance(envelope, legalEntity, code);
        transaction.executeWithoutResult(status -> publisher.publishEvent(record));
    }

    /** One audit row with the final subject columns, written directly; the table only refuses update and delete. */
    protected void auditRow(String action, UUID actor, String subjectKind, UUID subjectId, String outcome, String address, String method)
            throws SQLException {
        try (Connection audit = schemaConnection(P5Names.AUDIT_SCHEMA); PreparedStatement insert = audit.prepareStatement(
                "insert into audit_entries (id, action, actor_id, " + P5Names.SUBJECT_KIND + ", " + P5Names.SUBJECT_ID
                        + ", outcome_kind, source_address, login_method_kind, created_at) values (uuidv7(), ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, action);
            insert.setObject(2, actor);
            insert.setString(3, subjectKind);
            insert.setObject(4, subjectId);
            insert.setString(5, outcome);
            insert.setString(6, address);
            insert.setString(7, method);
            insert.setTimestamp(8, Timestamp.from(clock.instant()));
            insert.executeUpdate();
        }
    }

    protected List<Map<String, Object>> auditRows(String sql, Object... parameters) throws SQLException {
        return query(P5Names.AUDIT_SCHEMA, sql, parameters);
    }

    protected static Instant at(long secondsAfterNow) {
        return NOW.plusSeconds(secondsAfterNow);
    }
}
