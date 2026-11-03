package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.acceptance.DataScopeSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * #730 slice S3 (the HTTP API of the permission adjustments, F-1 card sections 7.1 and 7.3, contract items 10-16 as recorded in the
 * old F1_ACCEPTANCE.md): a small organisation with every kind of caller, and items written straight into
 * platform_identity.permission_adjustments in the valid shapes of the card (the listeners that generate them are slice S1/S2), so that
 * the API is judged on what it does with an item and not on how the item came to be. Paths, the {@code :uphold} and
 * {@code :mark-adjusted} commands and their field names are the contract's assumption.
 */
abstract class PermissionAdjustmentApiSupport extends DataScopeSupport {

    static final String CREATED = "2026-09-01T02:00:00Z";
    static final String DUE = "2026-09-08T02:00:00Z";
    static final String OWN_ITEM_ERROR = "PLATFORM_IDENTITY_OWN_PERMISSION_ADJUSTMENT_FORBIDDEN";
    /** Only viewed has no domain event and keeps this kebab name; event-born actions use the event type name (one-design ruling, #730 comment 6602101721). */
    static final String AUDIT_PREFIX = "platform.identity.permission-adjustment.";
    static final String UPHELD_ACTION = "platform.identity.PermissionAdjustmentUpheld";
    static final String ADJUSTED_ACTION = "platform.identity.PermissionAdjustmentAdjusted";

    private static final Set<String> TIMESTAMPS = Set.of("escalation_due_at", "created_at", "updated_at");

    /**
     * oda, oda2: organisation data administrators; auditor: security auditor; multi: security auditor and head of F1-C; headA: head of the
     * old unit, headB: head of the new unit (no roles); mover, mover2: holders of a sensitive role, the subjects of the items; odaSubject:
     * an organisation data administrator who is the subject of an item; owner: employed business owner; gone: separated; plain: nothing.
     */
    protected void apiWorld() throws Exception {
        unit("F1-ROOT", null);
        unit("F1-A", "F1-ROOT");
        unit("F1-B", "F1-ROOT");
        unit("F1-C", "F1-ROOT");
        position("F1-P1");
        for (String key : List.of("admin1", "oda", "oda2", "auditor", "multi", "headA", "headB", "mover", "mover2", "odaSubject", "owner", "gone", "plain")) {
            person(key, "F1-ROOT", "F1-P1");
        }
        head("F1-A", "headA");
        head("F1-B", "headB");
        head("F1-C", "multi");
        seedOrganisation();
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement separated = workforce
                .prepareStatement("update employees set employment_status = 'SEPARATED', separated_at = ? where id = ?")) {
            separated.setObject(1, OffsetDateTime.parse(CREATED));
            separated.setObject(2, stored(UUID.fromString(employeeIdOf("gone"))));
            separated.executeUpdate();
        }
        administrator("admin1");
        for (String key : List.of("oda", "oda2", "odaSubject")) {
            grantByOperations(key, ORG_DATA_ADMIN);
        }
        for (String key : List.of("auditor", "multi")) {
            grantByOperations(key, SECURITY_AUDITOR);
        }
        grantByOperations("mover", BUSINESS_ADMIN);
        grantByOperations("mover2", BUSINESS_ADMIN);
    }

    // ---- items --------------------------------------------------------------------------------------------------------------

    /** An open sensitive role review about the person, raised by a transfer between two units (constraint table rows 9-11). */
    protected UUID sensitiveItemAbout(String key) throws Exception {
        return insertItem(base("SENSITIVE_ROLE_REVIEW", "ASSIGNMENT_CHANGE").with("user_id", UUID.fromString(userId(key)))
                .with("trigger_personnel_command_id", UUID.randomUUID()).with("trigger_field_kind", "ORG_UNIT").with("before_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-A")))
                .with("after_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-B"))));
    }

    /** An open scope review about the person, for the head of the new unit (row 12); recipientKey null: the new unit has no head. */
    protected UUID scopeReviewAbout(String key, String recipientKey) throws Exception {
        return insertItem(base("SCOPE_REVIEW", "ASSIGNMENT_CHANGE").with("user_id", UUID.fromString(userId(key)))
                .with("trigger_personnel_command_id", UUID.randomUUID()).with("trigger_field_kind", "ORG_UNIT").with("before_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-A")))
                .with("after_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-B")))
                .with("recipient_employee_id", recipientKey == null ? null : UUID.fromString(employeeIdOf(recipientKey))));
    }

    /** An open scope review about a role that still names an inactive unit (RULE-387); about a role, not a person. */
    protected UUID inactiveUnitItem() throws Exception {
        UUID role;
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement any = identity.prepareStatement("select role_id from roles limit 1");
                ResultSet found = any.executeQuery()) {
            found.next();
            role = found.getObject(1, UUID.class);
        }
        return insertItem(base("SCOPE_REVIEW", "ORG_UNIT_INACTIVE").with("role_id", role).with("org_unit_id", UUID.randomUUID())
                .with("trigger_org_structure_command_id", UUID.randomUUID()));
    }

    protected static final class Item {
        final Map<String, Object> columns = new LinkedHashMap<>();

        Item with(String column, Object value) {
            columns.put(column, value);
            return this;
        }
    }

    private static Item base(String kind, String origin) {
        return new Item().with("permission_adjustment_id", UUID.randomUUID()).with("kind", kind).with("origin_kind", origin).with("status", "OPEN")
                .with("escalation_due_at", DUE).with("created_at", CREATED).with("updated_at", CREATED);
    }

    private UUID insertItem(Item item) throws SQLException {
        List<String> names = new ArrayList<>();
        List<String> marks = new ArrayList<>();
        for (String column : item.columns.keySet()) {
            names.add(column);
            marks.add(TIMESTAMPS.contains(column) ? "?::timestamptz" : "?");
        }
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement insert = identity.prepareStatement(
                "insert into permission_adjustments (" + String.join(", ", names) + ") values (" + String.join(", ", marks) + ")")) {
            int position = 1;
            for (Object value : item.columns.values()) {
                insert.setObject(position++, value);
            }
            insert.executeUpdate();
        }
        return (UUID) item.columns.get("permission_adjustment_id");
    }

    /** The stored row of the item as column name to value; timestamps come back as text of the instant. */
    protected Map<String, Object> rowOf(UUID item) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity
                .prepareStatement("select * from permission_adjustments where permission_adjustment_id = ?")) {
            select.setObject(1, item);
            try (ResultSet found = select.executeQuery()) {
                if (!found.next()) {
                    throw new AssertionError("no permission adjustment row " + item);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int column = 1; column <= found.getMetaData().getColumnCount(); column++) {
                    Object value = found.getObject(column);
                    row.put(found.getMetaData().getColumnName(column), value instanceof java.sql.Timestamp || value instanceof OffsetDateTime
                            ? found.getObject(column, OffsetDateTime.class).toInstant().toString() : value);
                }
                return row;
            }
        }
    }

    // ---- calls --------------------------------------------------------------------------------------------------------------

    protected MockHttpServletResponse read(String session, UUID item) throws Exception {
        return readAdjustment(session, item.toString());
    }

    protected MockHttpServletResponse command(String session, UUID item, String name, String ifMatch, String body) throws Exception {
        var request = post(PERMISSION_ADJUSTMENTS + "/" + item + ":" + name).with(csrf()).with(as(session)).contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(ifMatch == null ? request : request.header("If-Match", ifMatch)).andReturn().getResponse();
    }

    /** The command with the version as the organisation data administrator reads it. */
    protected MockHttpServletResponse commandWithCurrentVersion(String session, UUID item, String name, String body) throws Exception {
        return command(session, item, name, read(session("oda"), item).getHeader("ETag"), body);
    }

    protected MockHttpServletResponse listPage(String session, String... parameters) throws Exception {
        var request = get(PERMISSION_ADJUSTMENTS).with(as(session));
        for (int i = 0; i < parameters.length; i += 2) {
            request = request.param(parameters[i], parameters[i + 1]);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    protected List<String> listedIds(String session) throws Exception {
        return field(adjustments(session), "permissionAdjustmentId");
    }

    protected static Instant instantAt(MockHttpServletResponse answer, String path) throws Exception {
        return OffsetDateTime.parse(JsonPath.read(answer.getContentAsString(), path)).toInstant();
    }

    protected static String uphold(String reason, String ownerEmployeeId) {
        return "{\"reason\":\"" + reason + "\",\"businessOwnerEmployeeId\":\"" + ownerEmployeeId + "\"}";
    }

    protected static String adjustedBy(String member, String id) {
        return "{\"" + member + "\":\"" + id + "\"}";
    }
}
