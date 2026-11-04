package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.organization.workforce.events.OrgUnitDissolved;
import com.forcitis.one.organization.workforce.events.OrgUnitMerged;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.events.RoleRevised;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** #1244: real event delivery, completion barriers, direct inserts of valid row shapes and read-only observations of F-1 rows. */
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/testdata")
abstract class PermissionAdjustmentConsumerSupport extends PermissionAdjustmentApiSupport {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    protected void consumerWorld(String... unitCodes) throws Exception {
        unit("F1-ROOT", null);
        unit("F1-A", "F1-ROOT");
        unit("F1-B", "F1-ROOT");
        for (String code : unitCodes) {
            unit(code, "F1-ROOT");
        }
        position("F1-P1");
        for (String key : List.of("admin1", "oda", "auditor", "subject", "other", "roleless", "auditSubject", "replaySubject", "noAccount", "noItems")) {
            person(key, "F1-ROOT", "F1-P1");
        }
        seedOrganisation();
        administrator("admin1");
        grantByOperations("oda", ORG_DATA_ADMIN);
        grantByOperations("auditor", SECURITY_AUDITOR);
    }

    protected UUID unitId(String code) throws Exception {
        return UUID.fromString(orgUnitId(session("oda"), code));
    }

    protected String customRole(String code, CustomOrgUnit... units) throws Exception {
        return ordinaryRole(administrator("admin1"), code, "CUSTOM_ORG_UNITS", List.of(units), ORG_UNIT_READ);
    }

    protected EventEnvelope envelope(String type, String aggregateType, UUID aggregate) {
        return new EventEnvelope(Uuids.timeOrdered(clock.instant()), type, 1, clock.instant(), aggregateType, aggregate, 1,
                Uuids.timeOrdered(clock.instant()), null, ActorType.SYSTEM, SystemActors.idOf("test.envelope"),
                null, TODAY, null, List.of());
    }

    protected EmployeeSeparated separation(String person) {
        UUID employee = UUID.fromString(employeeIdOf(person));
        return new EmployeeSeparated(envelope("organization.workforce.EmployeeSeparated", "Employee", employee),
                new PersonnelCommandId(Uuids.timeOrdered(clock.instant())), new EmployeeId(employee), TODAY);
    }

    protected OrgUnitDissolved dissolution(UUID unit) {
        UUID command = Uuids.timeOrdered(clock.instant());
        return new OrgUnitDissolved(envelope("organization.workforce.OrgUnitDissolved", "OrgStructureCommand", command),
                new OrgStructureCommandId(command), new OrgUnitId(unit), TODAY);
    }

    protected OrgUnitMerged merge(UUID unit, UUID successor) {
        UUID command = Uuids.timeOrdered(clock.instant());
        return new OrgUnitMerged(envelope("organization.workforce.OrgUnitMerged", "OrgStructureCommand", command),
                new OrgStructureCommandId(command), new OrgUnitId(unit), new OrgUnitId(successor), TODAY);
    }

    protected RoleRevised keptVersion(String role) throws SQLException {
        return new RoleRevised(envelope("platform.identity.RoleRevised", "Role", UUID.fromString(role)), new RoleId(UUID.fromString(role)),
                new RoleVersionId((UUID) currentVersion(role).get("role_version_id")));
    }

    protected void publish(DomainEvent event) {
        transaction.executeWithoutResult(status -> publisher.publishEvent(event));
    }

    protected void publishAndAwait(DomainEvent event) {
        publish(event);
        identityHandled(event.envelope().eventId());
    }

    /** A negative assertion follows completed delivery, rather than winning a race against an asynchronous listener. */
    protected void identityHandled(UUID eventId) {
        eventually(() -> {
            List<Map<String, Object>> publications = publications(eventId);
            assertThat(publications).as("registered identity delivery for " + eventId)
                    .anyMatch(row -> String.valueOf(row.get("listener_id")).contains(".platform.identity."));
            assertThat(publications).as("all subscribers completed event " + eventId)
                    .allSatisfy(row -> assertThat(row.get("completion_date")).isNotNull());
        });
    }

    private List<Map<String, Object>> publications(UUID eventId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String table : List.of("event_publication", "event_publication_archive")) {
            rows.addAll(select("platform_integration", "select listener_id, completion_date from " + table + " where serialized_event like ?",
                    "%" + eventId + "%"));
        }
        return rows;
    }

    protected List<Map<String, Object>> itemsForUnit(UUID unit) throws SQLException {
        return select("platform_identity", "select * from permission_adjustments where org_unit_id = ?", unit);
    }

    protected UUID itemFor(String role, UUID unit) throws SQLException {
        List<Map<String, Object>> rows = select("platform_identity",
                "select permission_adjustment_id from permission_adjustments where role_id = ?::uuid and org_unit_id = ?", role, unit);
        assertThat(rows).as("one item for role " + role + " and unit " + unit).hasSize(1);
        return (UUID) rows.getFirst().get("permission_adjustment_id");
    }

    protected Map<String, Object> currentVersion(String role) throws SQLException {
        List<Map<String, Object>> rows = select("platform_identity", """
                select role_version_id, version_audit_entry_id from role_versions
                 where role_id = ?::uuid and effective_to_at is null""", role);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    protected UUID sourceTwoItem(String role, UUID unit, boolean upheld) throws Exception {
        Item item = base(SCOPE_REVIEW, "ORG_UNIT_INACTIVE").with("role_id", UUID.fromString(role)).with("org_unit_id", unit)
                .with("trigger_org_structure_command_id", UUID.randomUUID());
        return insertItem(upheld ? upheld(item) : item);
    }

    protected Item upheld(Item item) throws Exception {
        item.with("status", "UPHELD").with("upheld_at", clock.instant().toString()).with("upheld_by", UUID.fromString(userId("oda")))
                .with("uphold_reason", "Synthetic continuing need");
        if (SENSITIVE_ROLE_REVIEW.equals(item.columns.get("kind"))) {
            LocalDate today = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
            LocalDate nextQuarter = today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1).plusMonths(3);
            item.with("business_owner_employee_id", UUID.fromString(employeeIdOf("other")))
                    .with("uphold_expires_at", nextQuarter.atStartOfDay(BUSINESS_ZONE).toInstant().toString());
        }
        return item;
    }

    protected List<Map<String, Object>> auditsOf(String action, UUID item) throws Exception {
        return auditEntries(action).stream().filter(row -> action.equals(row.get("action")) && item.toString().equals(row.get("subjectId"))).toList();
    }

    /** The registry has one row per subscriber; count distinct envelope ids across live and archived publications. */
    protected List<Map<String, Object>> eventsOf(String type, UUID item) throws SQLException {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (String table : List.of("event_publication", "event_publication_archive")) {
            for (Map<String, Object> row : select("platform_integration",
                    "select serialized_event from " + table + " where event_type like ? and serialized_event like ?", "%." + type, "%" + item + "%")) {
                Map<String, Object> event = JsonPath.read((String) row.get("serialized_event"), "$");
                Map<?, ?> envelope = (Map<?, ?>) event.get("envelope");
                unique.put(String.valueOf(envelope.get("eventId")), event);
            }
        }
        return List.copyOf(unique.values());
    }

    protected void revisionHandled(String role) throws SQLException {
        UUID version = (UUID) currentVersion(role).get("role_version_id");
        eventually(() -> {
            List<Map<String, Object>> events = eventsOf("RoleRevised", version);
            assertThat(events).as("HTTP revision published RoleRevised for " + version).isNotEmpty();
            for (Map<String, Object> event : events) {
                Map<?, ?> envelope = (Map<?, ?>) event.get("envelope");
                identityHandled(UUID.fromString(String.valueOf(envelope.get("eventId"))));
            }
        });
    }

    protected static String idValue(Object value) {
        return String.valueOf(value instanceof Map<?, ?> wrapped ? wrapped.get("value") : value);
    }

    protected List<Map<String, Object>> select(String schema, String sql, Object... arguments) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = schemaConnection(schema); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                        row.put(result.getMetaData().getColumnLabel(column), result.getObject(column));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
