package com.forcitis.one.acceptance.platform.identity.grant_store;

import com.forcitis.one.acceptance.EmploymentRelationshipEventIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ground of the #732 part A acceptance (option C: platform_identity.account_role_grants is the only grant store). Everything goes through
 * the public paths: the HTTP interface, the operations commands, the published EmployeeSeparated event of organization.workforce, and
 * identity's own schema (grants, role_versions, sessions, emergency_admins, browser_identifiers) plus the audit and event-registry rows
 * those writes leave. The grant columns are those of ADR-0034 decision 2 as ADR-0055 decision 3 item 5 and the DD sign-off of #732 fix them.
 */
abstract class GrantStoreSupport extends AccountsAcceptanceSupport {

    static final String ROLES = "/api/v1/platform/roles";
    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    static final String CURRENT = "/api/v1/platform/sessions:current";

    static final String REVOKED_ACTION = "platform.identity.account.role.revoked";

    /** Permission codes that need no second person and are in none of the seeded roles an ordinary account holds (as in role_versioning). */
    static final List<String> PLAIN_CODES = List.of("organization.workforce:org-unit:read", "cost.timesheet:timesheet:submit",
            "platform.numbering:numbering-rule:read", "organization.attendance:work-calendar:read", "platform.numbering:legacy-number:read");

    /** One row of platform_identity.account_role_grants with the code of its role. */
    record Grant(UUID id, UUID userId, UUID roleId, String roleCode, Instant from, Instant to, String channel, UUID grantedBy, UUID grantAuditEntryId,
                 String cause, UUID revokedBy, UUID separationCommandId, UUID revocationAuditEntryId) {
        boolean open() {
            return to == null;
        }
    }

    record AuditRow(UUID id, String action, String detail, UUID subjectId) {
    }

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    protected TransactionTemplate transaction;

    @Autowired
    private com.forcitis.one.platform.identity.application.OperationsCommands operationsCommands;

    // ---- people and roles ----

    /** An employed person with an account (opened by their first sign-in, so it holds the default role) and the roles granted by operations command before that. */
    SignedIn person(String... roleCodes) throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID employee = newEmployeeId();
        employee(employee, unit, null);
        for (String code : roleCodes) {
            grantByOperations(employee, code);
        }
        return signIn(employee);
    }

    /** A super_admin holder, signed in, in the administrator mode of the session. */
    SignedIn administrator() throws Exception {
        return administratorMode(person("super_admin"));
    }

    static String definitionJson(String code, String name, String scope, Collection<String> permissionCodes) {
        String codes = permissionCodes.stream().sorted().distinct().map(c -> "\"" + c + "\"").collect(Collectors.joining(","));
        return "{" + (code == null ? "" : "\"code\":\"" + code + "\",") + "\"name\":\"" + name + "\",\"enabled\":true,\"accessScope\":\"" + scope
                + "\",\"permissionCodes\":[" + codes + "]}";
    }

    /** A custom role holding the given permission codes, made by the administrator; returns its code. */
    String customRole(SignedIn administrator, String scope, String... permissionCodes) throws Exception {
        String code = "gs_" + UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse created = mvc.perform(post(ROLES).with(csrf()).with(session(administrator)).contentType(MediaType.APPLICATION_JSON)
                .content(definitionJson(code, "Grant store " + code, scope, List.of(permissionCodes)))).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        return code;
    }

    UUID roleId(String code) throws SQLException {
        return fromStored(single("select role_id from roles where lower(code) = lower(?)", code));
    }

    // ---- the HTTP paths ----

    String etagOfAccount(SignedIn caller, UUID user) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + user).with(session(caller))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return read.getHeader("ETag");
    }

    MockHttpServletResponse replaceRoles(SignedIn caller, UUID user, Collection<String> roleCodes) throws Exception {
        String codes = roleCodes.stream().sorted().map(c -> "\"" + c + "\"").collect(Collectors.joining(","));
        return mvc.perform(post(ACCOUNTS + user + ":replace-roles").header("If-Match", etagOfAccount(caller, user)).with(csrf()).with(session(caller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[" + codes + "]}")).andReturn().getResponse();
    }

    void replacedRoles(SignedIn caller, UUID user, Collection<String> roleCodes) throws Exception {
        MockHttpServletResponse replaced = replaceRoles(caller, user, roleCodes);
        assertThat(replaced.getStatus()).as("replace roles of %s with %s: %s", user, roleCodes, replaced.getContentAsString()).isEqualTo(204);
    }

    List<String> roleCodesOfAccount(SignedIn caller, UUID user) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + user).with(session(caller))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return JsonPath.read(read.getContentAsString(), "$.roleCodes");
    }

    MockHttpServletResponse deleteRole(SignedIn caller, String code) throws Exception {
        return deleteRole(caller, code, roleEtag(caller, code));
    }

    String roleEtag(SignedIn caller, String code) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ROLES + "/" + roleId(code)).with(session(caller))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return read.getHeader("ETag");
    }

    MockHttpServletResponse deleteRole(SignedIn caller, String code, String etag) throws Exception {
        return mvc.perform(delete(ROLES + "/" + roleId(code)).header("If-Match", etag).with(csrf()).with(session(caller))).andReturn().getResponse();
    }

    /** The status of the current-user profile with the session of the sign-in: 200 while the session lives, 401 once it is revoked. */
    int profileStatus(SignedIn who) throws Exception {
        return mvc.perform(get(CURRENT).with(session(who))).andReturn().getResponse().getStatus();
    }

    /** The permission codes the session's profile reports (ordinary mode). */
    List<String> permissionsOf(SignedIn who) throws Exception {
        MockHttpServletResponse current = mvc.perform(get(CURRENT).with(session(who))).andReturn().getResponse();
        assertThat(current.getStatus()).as(current.getContentAsString()).isEqualTo(200);
        return JsonPath.read(current.getContentAsString(), "$.permissionCodes");
    }

    int operationsRevoke(UUID employee, String roleCode) {
        return operation("--operation=revoke-role", "--employee-id=" + text(employee), "--role-code=" + roleCode);
    }

    int operation(String... arguments) {
        ensureAttestors();
        List<String> all = new ArrayList<>(List.of(arguments));
        all.add("--operator=" + OPERATOR_ID);
        all.add("--witness=" + WITNESS_ID);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream stream = new java.io.PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8);
        return operationsCommands.run(new org.springframework.boot.DefaultApplicationArguments(all.toArray(String[]::new)), stream, stream);
    }

    // ---- the separation event ----

    /** What organization.workforce publishes when a separation command takes effect (ADR-0068 item 1.9); returns the event id. */
    UUID publishSeparated(UUID employee, UUID command, LocalDate effectiveDate) {
        EmployeeSeparated event = separated(employee, command, effectiveDate);
        publish(event);
        return event.envelope().eventId();
    }

    EmployeeSeparated separated(UUID employee, UUID command, LocalDate effectiveDate) {
        return new EmployeeSeparated(TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", employee), new PersonnelCommandId(command),
                employeeIdOf(employee), effectiveDate,
                EmploymentRelationshipEventIds.of(employee));
    }

    /** What organization.workforce publishes when a reinstatement takes effect after a separation (ADR-0068 items 1.5, 1.10); returns the event id. */
    UUID publishReinstated(UUID employee, UUID command, LocalDate effectiveDate) {
        EmployeeReinstated event = new EmployeeReinstated(TestEnvelopes.of("organization.workforce.EmployeeReinstated", "Employee", employee),
                new PersonnelCommandId(command), employeeIdOf(employee), effectiveDate,
                EmploymentRelationshipEventIds.of(employee));
        publish(event);
        return event.envelope().eventId();
    }

    void publish(Object event) {
        transaction.executeWithoutResult(status -> publisher.publishEvent(event));
    }

    static UUID newCommandId() {
        return Uuids.timeOrdered(NOW);
    }

    // ---- reading the grant store ----

    List<Grant> grants(UUID user) throws SQLException {
        return grantsWhere("g.user_id = ?", user);
    }

    List<Grant> grants(UUID user, String roleCode) throws SQLException {
        return grantsWhere("g.user_id = ? and lower(r.code) = lower(?)", user, roleCode);
    }

    List<Grant> openGrants(UUID user) throws SQLException {
        return grants(user).stream().filter(Grant::open).toList();
    }

    List<String> openRoleCodes(UUID user) throws SQLException {
        return openGrants(user).stream().map(Grant::roleCode).sorted().toList();
    }

    /** The grants in force at the moment by the as-of predicate of ADR-0034 decision 4 item 3 (DD sign-off of #732, section D). */
    List<Grant> grantsAsOf(UUID user, Instant moment) throws SQLException {
        return grantsWhere("g.user_id = ? and tstzrange(g.effective_from_at, g.effective_to_at, '[)') @> ?::timestamptz", user, OffsetDateTime.ofInstant(moment, ZoneOffset.UTC));
    }

    long grantRows() throws SQLException {
        return ((Number) single("select count(*) from account_role_grants")).longValue();
    }

    long openGrantRows() throws SQLException {
        return ((Number) single("select count(*) from account_role_grants where effective_to_at is null")).longValue();
    }

    private List<Grant> grantsWhere(String where, Object... parameters) throws SQLException {
        List<Grant> rows = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select g.*, r.code from account_role_grants g join roles r on r.role_id = g.role_id where %s
                 order by g.effective_from_at, g.created_at, g.account_role_grant_id""".formatted(where))) {
            bind(select, parameters);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Grant(rs.getObject("account_role_grant_id", UUID.class), rs.getObject("user_id", UUID.class), rs.getObject("role_id", UUID.class),
                            rs.getString("code"), instant(rs, "effective_from_at"), instant(rs, "effective_to_at"), rs.getString("grant_channel_kind"),
                            rs.getObject("granted_by", UUID.class), rs.getObject("grant_audit_entry_id", UUID.class), rs.getString("revocation_cause_kind"),
                            rs.getObject("revoked_by", UUID.class), rs.getObject("revocation_separation_command_id", UUID.class),
                            rs.getObject("revocation_audit_entry_id", UUID.class)));
                }
            }
        }
        return rows;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /** Writes a grant row the way a past act would have left it, for histories the test needs ready-made; the role must have a current version. */
    UUID insertGrant(UUID user, UUID role, Instant from, Instant to) throws SQLException {
        UUID id = Uuids.timeOrdered(from);
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement insert = identity.prepareStatement("""
                insert into account_role_grants (account_role_grant_id, user_id, role_id, effective_from_at, effective_to_at, granted_by, grant_channel_kind,
                       grant_authorization_request_id, migration_batch_id, grant_audit_entry_id, revoked_by, revocation_cause_kind,
                       revocation_authorization_request_id, revocation_separation_command_id, revocation_audit_entry_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, null, 'OPERATIONS', null, null, ?, null, ?, null, null, ?, ?, ?)""")) {
            bind(insert, id, user, role, offset(from), offset(to), UUID.randomUUID(), to == null ? null : "OPERATIONS", to == null ? null : UUID.randomUUID(),
                    offset(from), offset(to == null ? from : to));
            insert.executeUpdate();
        }
        return id;
    }

    /** Closes an open grant as an operations revoke would have, at the moment given. */
    void closeGrant(UUID grantId, Instant at) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement update = identity.prepareStatement("""
                update account_role_grants set effective_to_at = ?, revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = ?, updated_at = ?
                 where account_role_grant_id = ? and effective_to_at is null""")) {
            bind(update, offset(at), UUID.randomUUID(), offset(at), grantId);
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
    }

    /** A person's open grants all closed by hand, as a fixture; the rows stay. */
    void closeAllGrants(UUID user, Instant at) throws SQLException {
        for (Grant grant : openGrants(user)) {
            closeGrant(grant.id(), at.isBefore(grant.from()) ? grant.from() : at);
        }
    }

    // ---- audit and event registry ----

    AuditRow audit(UUID id) throws SQLException {
        try (Connection audit = schemaConnection("platform_audit"); PreparedStatement select = audit.prepareStatement(
                "select id, action, detail, subject_id from audit_entries where id = ?")) {
            select.setObject(1, id);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).as("the audit entry %s exists", id).isTrue();
                return new AuditRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class));
            }
        }
    }

    long auditCount(String action) throws SQLException {
        try (Connection audit = schemaConnection("platform_audit"); PreparedStatement select = audit.prepareStatement("select count(*) from audit_entries where action = ?")) {
            select.setString(1, action);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** The serialized events of the type (name suffix) that mention the text, from the publication registry; one entry per distinct event id. */
    List<String> publishedEvents(String eventTypeSuffix, String mention) throws SQLException {
        java.util.Map<String, String> byEventId = new java.util.LinkedHashMap<>();
        java.util.regex.Pattern eventId = java.util.regex.Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"");
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement select = registry.prepareStatement("select serialized_event from " + table + " where event_type like ? and serialized_event like ?")) {
                    select.setString(1, "%." + eventTypeSuffix);
                    select.setString(2, "%" + mention + "%");
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            String json = rs.getString(1);
                            java.util.regex.Matcher found = eventId.matcher(json);
                            byEventId.put(found.find() ? found.group(1) : json, json);
                        }
                    }
                }
            }
        }
        return List.copyOf(byEventId.values());
    }

    // ---- sessions, emergency accounts, browser identifiers ----

    long sessionRows(UUID user) throws SQLException {
        return ((Number) single("select count(*) from sessions where principal_name = ?", user.toString())).longValue();
    }

    boolean emergencyAdminEnabled(UUID user) throws SQLException {
        return (Boolean) single("select is_enabled from emergency_admins where user_id = ?", user);
    }

    long liveBrowserIdentifiers(UUID user) throws SQLException {
        return ((Number) single("select count(*) from browser_identifiers where user_id = ? and retired_at is null", user)).longValue();
    }

    long accountRows() throws SQLException {
        return ((Number) single("select count(*) from user_accounts")).longValue();
    }

    // ---- plumbing ----

    Object single(String sql, Object... parameters) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement(sql)) {
            bind(select, parameters);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).as(sql).isTrue();
                return rs.getObject(1);
            }
        }
    }

    void execute(String sql, Object... parameters) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement statement = identity.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.execute();
        }
    }

    static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }
}
