package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Role management through the public HTTP paths (OpenAPI unchanged, ADR-0034 影响面「OpenAPI：不变」) by a super_admin holder in the
 * administrator mode, and the role_versions rows read back from identity's own schema (ADR-0034-IMPL card 7.1 columns). Permission codes
 * used here are outside SensitivePermissions, so no change waits for a second person.
 */
abstract class RoleVersioningSupport extends AccountsAcceptanceSupport {

    static final String ROLES = "/api/v1/platform/roles";

    /** One row of platform_identity.role_versions, as card 7.1 declares the columns. */
    record VersionRow(UUID roleVersionId, UUID roleId, int versionNumber, Instant from, Instant to, String roleName, String roleStatus,
                      String accessScopeKind, List<String> permissionCodes, String changeChannelKind, UUID recordedBy, String changeReason,
                      Integer declarationRevisionNumber, UUID versionAuditEntryId, UUID closedBy, String closureReason, UUID closureAuditEntryId) {
        boolean open() {
            return to == null;
        }
    }

    /** What a role definition is, as sent in RoleRequest (code is fixed at creation). */
    record Definition(String name, boolean enabled, String accessScope, List<String> permissionCodes) {
        Definition {
            permissionCodes = permissionCodes.stream().sorted().distinct().toList();
        }

        String json(String code) {
            String codes = permissionCodes.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(","));
            return "{" + (code == null ? "" : "\"code\":\"" + code + "\",") + "\"name\":\"" + name + "\",\"enabled\":" + enabled
                    + ",\"accessScope\":\"" + accessScope + "\",\"permissionCodes\":[" + codes + "]}";
        }

        String status() {
            return enabled ? "ENABLED" : "DISABLED";
        }
    }

    /**
     * Non-sensitive codes that a custom role may hold (none reserved to the administrator mode), none of them in the seeded employee role (card 7.2
     * seed). The sync run read code that stood second went with the old sync (#934; ADR-0055 section 6) and is no longer grantable.
     */
    static final List<String> PLAIN_CODES = List.of("organization.workforce:org-unit:read", "cost.timesheet:timesheet:submit",
            "platform.numbering:numbering-rule:read", "organization.attendance:work-calendar:read", "platform.numbering:legacy-number:read");

    /** A super_admin holder (operations command), signed in through WeCom and in the administrator mode of their own session. */
    SignedIn roleAdministrator() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID employee = newEmployeeId();
        employee(employee, unit, null);
        grantByOperations(employee, "super_admin");
        return administratorMode(signIn(employee));
    }

    MockHttpServletResponse createRole(SignedIn caller, String code, Definition definition) throws Exception {
        return mvc.perform(post(ROLES).with(csrf()).with(session(caller)).contentType(MediaType.APPLICATION_JSON).content(definition.json(code)))
                .andReturn().getResponse();
    }

    UUID createdRole(SignedIn caller, String code, Definition definition) throws Exception {
        MockHttpServletResponse created = createRole(caller, code, definition);
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        return UUID.fromString(JsonPath.read(created.getContentAsString(), "$.roleId"));
    }

    String etagOf(SignedIn caller, UUID role) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ROLES + "/" + role).with(session(caller))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return read.getHeader("ETag");
    }

    MockHttpServletResponse updateRole(SignedIn caller, UUID role, Definition definition) throws Exception {
        String etag = etagOf(caller, role);
        return mvc.perform(put(ROLES + "/" + role).header("If-Match", etag).with(csrf()).with(session(caller)).contentType(MediaType.APPLICATION_JSON)
                .content(definition.json(null))).andReturn().getResponse();
    }

    MockHttpServletResponse deleteRole(SignedIn caller, UUID role) throws Exception {
        String etag = etagOf(caller, role);
        return mvc.perform(delete(ROLES + "/" + role).header("If-Match", etag).with(csrf()).with(session(caller))).andReturn().getResponse();
    }

    /** Every role code GET /roles lists, page after page. */
    List<String> listedRoleCodes(SignedIn caller) throws Exception {
        List<String> codes = new ArrayList<>();
        String cursor = null;
        do {
            var request = get(ROLES).param("limit", "100").with(session(caller));
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }
            MockHttpServletResponse page = mvc.perform(request).andReturn().getResponse();
            assertThat(page.getStatus()).as(page.getContentAsString()).isEqualTo(200);
            codes.addAll(JsonPath.read(page.getContentAsString(), "$.items[*].code"));
            cursor = JsonPath.read(page.getContentAsString(), "$.nextCursor");
        } while (cursor != null);
        return codes;
    }

    List<VersionRow> versionsOf(UUID role) throws SQLException {
        return versions("select * from role_versions where role_id = ? order by version_number", role);
    }

    List<VersionRow> versions(String sql, Object... parameters) throws SQLException {
        List<VersionRow> rows = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(new VersionRow(rs.getObject("role_version_id", UUID.class), rs.getObject("role_id", UUID.class), rs.getInt("version_number"),
                            instant(rs.getTimestamp("effective_from_at")), instant(rs.getTimestamp("effective_to_at")), rs.getString("role_name"),
                            rs.getString("role_status"), rs.getString("access_scope_kind"), codes(rs.getArray("permission_codes")),
                            rs.getString("change_channel_kind"), rs.getObject("recorded_by", UUID.class), rs.getString("change_reason"),
                            (Integer) rs.getObject("declaration_revision_number"), rs.getObject("version_audit_entry_id", UUID.class),
                            rs.getObject("closed_by", UUID.class), rs.getString("closure_reason"), rs.getObject("closure_audit_entry_id", UUID.class)));
                }
            }
        }
        return rows;
    }

    long count(String sql, Object... parameters) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    void execute(String sql, Object... parameters) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement statement = identity.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.execute();
        }
    }

    static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    static List<String> codes(Array array) throws SQLException {
        return array == null ? null : Arrays.asList((String[]) array.getArray());
    }

    static List<String> sorted(Collection<String> codes) {
        return codes.stream().sorted().toList();
    }

    /**
     * ADR-0034 decision 1 items 2, 4 and 6 on one role's versions: numbered 1..n without gaps, at most one open and it is the last, each
     * closed one ends where the next begins (the closing moment and the new start are the same value), no interval is inverted.
     */
    static void assertWellFormedHistory(List<VersionRow> versions) {
        assertThat(versions).as("a role has at least one version").isNotEmpty();
        for (int i = 0; i < versions.size(); i++) {
            VersionRow version = versions.get(i);
            assertThat(version.versionNumber()).as("version numbers run 1..n: %s", versions).isEqualTo(i + 1);
            if (version.to() != null) {
                assertThat(version.to()).as("no inverted interval: %s", version).isAfterOrEqualTo(version.from());
            }
            if (i < versions.size() - 1) {
                assertThat(version.to()).as("only the last version may be open: %s", versions).isNotNull();
                assertThat(versions.get(i + 1).from()).as("the next version starts where this one was closed: %s", versions).isEqualTo(version.to());
            }
        }
        assertThat(versions.stream().filter(VersionRow::open).count()).as("at most one current version").isLessThanOrEqualTo(1);
    }
}
