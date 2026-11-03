package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ground of the #871 personnel command tests: a small world (one org unit, one legal entity, employed people with a migrated initial
 * employee version and assignment version), callers, and reads of the workforce schema. Tables are read by their agreed names; the
 * employee and assignment version tables carry the signed-off columns of #880 (change_channel_kind and the supersede columns).
 */
abstract class PersonnelCommandSupport extends AccountsAcceptanceSupport {

    /** The day every seeded employee's initial (migrated) versions start. */
    protected static final LocalDate SINCE = LocalDate.of(2026, 1, 1);

    @Autowired
    private ApplicationContext context;

    protected UUID unit;
    protected UUID legalEntity;
    protected SignedIn administrator;

    /** A person with an account: the employee behind it and the login session. */
    protected record Staff(UUID employeeId, SignedIn session) {
        UUID userId() {
            return session.userId();
        }
    }

    protected Staff hr;
    private SecurityContext acting;

    @BeforeEach
    void buildTheWorld() throws Exception {
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID admin = newEmployeeId();
        employee(admin, unit, null);
        grantByOperations(admin, "org_data_admin");
        administrator = signIn(admin);
        legalEntity = createLegalEntity(administrator, newLegalEntityCode("PC"), "Synthetic personnel command entity");
        hr = buildPersonnelSpecialist();
        if (hr != null) {
            actIn(hr);
        }
    }

    /** The 人事专员 of the world; the org-structure tests (#906) tolerate the role not being seeded yet, so that their red run shows their own reasons. */
    protected Staff buildPersonnelSpecialist() throws Exception {
        return staff(PERSONNEL_SPECIALIST_ROLE);
    }

    /** Sets the thread's security context to the acting caller again (see {@link #actIn}); the org-structure command tests (#906) share it. */
    protected void restoreActing() {
        if (acting == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.setContext(acting);
        }
    }

    protected PersonnelCommandsProbe commands() {
        restoreActing();
        return PersonnelCommandsProbe.of(context, AccountsAcceptanceSupport::employeeIdOf);
    }

    // ---- people ----

    /** An employee with an account and the given seeded system roles (granted before the login, so the session carries them). */
    protected Staff staff(String... roles) throws Exception {
        UUID id = newEmployeeId();
        employee(id, unit, null);
        for (String role : roles) {
            grantByOperations(id, role);
        }
        return new Staff(id, signIn(id));
    }

    /** An employed person with migrated initial versions from {@link #SINCE}, attributed to the world's legal entity. */
    protected UUID employed(String phase) throws Exception {
        return employed(phase, true);
    }

    /**
     * The migrated assignment version carries a position by default: a COMMAND-channel assignment row must have one (user decision of
     * 2026-10-30, Q8; W2 section 3.4), so a transfer that names only a unit copies it. Without a position the row is a legacy one.
     */
    protected UUID employedWithoutPosition(String phase) throws Exception {
        return employed(phase, false);
    }

    private UUID employed(String phase, boolean withPosition) throws Exception {
        return employedIn(unit, phase, withPosition);
    }

    /** As {@link #employed}, in the given unit (the org-structure command tests of #906 need people in several units). */
    protected UUID employedIn(UUID orgUnit, String phase, boolean withPosition) throws Exception {
        return employedAs(orgUnit, phase, withPosition ? newPosition() : null);
    }

    /** As {@link #employedIn}, holding the given position (null: a legacy assignment without one). */
    protected UUID employedAs(UUID orgUnit, String phase, UUID position) throws Exception {
        UUID id = newEmployeeId();
        employee(id, orgUnit, null);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                      employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                      change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, 0, 'EMPLOYED', ?, false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(id), SINCE, phase, SINCE, OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC));
            // #880 adds the command columns to assignment_versions; until it lands the seed is left out so that the tests fail on the missing
            // personnel command api, which is the point of the red run, and not on the seed. With #880 the row is always written.
            if (!query(workforce, "select 1 as present from information_schema.columns where table_schema = 'organization_workforce' and table_name = ? and column_name = ?",
                    ASSIGNMENT_VERSIONS, "change_channel_kind").isEmpty()) {
                update(workforce, """
                    insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                      change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(id), SINCE, stored(orgUnit), position, OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC));
            }
        }
        attributeEmployee(administrator, id, legalEntity);
        return id;
    }

    protected UUID newUnit() throws Exception {
        UUID other = newOrgUnitId();
        orgUnit(other, null, null);
        return other;
    }

    protected UUID newPosition() throws Exception {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "insert into positions (id, code, name, status) values (?, ?, 'Synthetic position', 'ACTIVE')", id, "PC" + id.toString().substring(24));
        }
        return id;
    }

    // ---- acting ----

    /**
     * The caller of the next api calls is this person, in the production session of their login (the roles they were granted decide the
     * permissions). MockMvc calls made by the helpers (attribution, audit queries) clear the thread's security context, so the context is
     * kept here and set again just before every api call, see {@link #commands()}.
     */
    protected void actIn(Staff who) throws Exception {
        MockHttpServletResponse current = mvc.perform(get("/api/v1/platform/sessions:current").with(session(who.session()))).andReturn().getResponse();
        assertThat(current.getStatus()).as("the session's own profile: " + current.getContentAsString()).isEqualTo(200);
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String code : JsonPath.<List<String>>read(current.getContentAsString(), "$.permissionCodes")) {
            authorities.add(new SimpleGrantedAuthority(code));
        }
        if ("ORDINARY".equals(JsonPath.<String>read(current.getContentAsString(), "$.mode"))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        }
        // The stored context of a login carries no authorities (the session filter fills them in per HTTP request), and the api is called in
        // process: so the production authentication of the login is kept and given the permissions the production path resolved from the roles.
        actInSession(who.session());
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        Method refresh = login.getClass().getDeclaredMethod("withAuthorities", java.util.Collection.class, boolean.class);
        refresh.setAccessible(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication((Authentication) refresh.invoke(login, authorities, true));
        acting = context;
        SecurityContextHolder.setContext(context);
    }

    /** For worker threads of a concurrency test: the caller set by {@link #actIn} is this thread's caller too (security contexts are per thread). */
    protected void actAsTheCallerOnThisThread() {
        if (acting == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.setContext(acting);
        }
    }

    /** Nobody is signed in for the next api calls. */
    protected void actAsNobody() {
        acting = null;
    }

    // ---- entering ----

    protected UUID enter(UUID employee, String kind, LocalDate effective) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, employee);
        fields.put(COMMAND_KIND, kind);
        fields.put(EFFECTIVE_DATE, effective);
        return commands().enter(fields);
    }

    protected UUID transfer(UUID employee, LocalDate effective, UUID toUnit) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, employee);
        fields.put(COMMAND_KIND, TRANSFER);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(TARGET_ORG_UNIT_ID, toUnit);
        return commands().enter(fields);
    }

    protected UUID separate(UUID employee, LocalDate lastWorkingDay) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, employee);
        fields.put(COMMAND_KIND, SEPARATE);
        fields.put(LAST_WORKING_DAY, lastWorkingDay);
        return commands().enter(fields);
    }

    protected UUID reinstate(UUID employee, LocalDate effective, UUID toUnit, UUID position) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, employee);
        fields.put(COMMAND_KIND, REINSTATE);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(TARGET_ORG_UNIT_ID, toUnit);
        fields.put(TARGET_POSITION_ID, position);
        fields.put(IS_TARGET_FEE_EARNER, false);
        fields.put(TARGET_EMPLOYMENT_PHASE_KIND, PROBATION);
        return commands().enter(fields);
    }

    /** A HELD separation and its hold, written straight into the workforce schema (creating holds is B4 and B6). */
    protected record Held(UUID employee, UUID command, UUID hold) {
    }

    protected Held held() throws Exception {
        UUID employee = employed(REGULAR);
        UUID command = Uuids.timeOrdered(NOW);
        UUID hold = Uuids.timeOrdered(NOW);
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                    values (?, ?, 'SEPARATE', ?, 'HELD', ?, ?, ?)""", command, stored(employee), TODAY.plusDays(10), at, hr.userId(), at);
            update(workforce, "insert into separation_holds (separation_hold_id, personnel_command_id, employee_id, status, created_at, updated_at) values (?, ?, ?, 'HELD', ?, ?)",
                    hold, command, stored(employee), at, at);
        }
        return new Held(employee, command, hold);
    }

    // ---- reading the workforce schema ----

    protected List<Map<String, Object>> rows(String sql, Object... args) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            return query(workforce, sql, args);
        }
    }

    protected Map<String, Object> command(UUID id) throws SQLException {
        List<Map<String, Object>> found = rows("select * from " + COMMANDS + " where personnel_command_id = ?", id);
        assertThat(found).as("a row of " + COMMANDS + " for " + id).hasSize(1);
        return found.get(0);
    }

    protected long commandCount(UUID employee) throws SQLException {
        return (long) rows("select count(*) as n from " + COMMANDS + " where employee_id = ?", stored(employee)).get(0).get("n");
    }

    /** Every row of a version table of the employee, superseded ones too, oldest first. */
    protected List<Map<String, Object>> allVersions(String table, UUID employee) throws SQLException {
        return rows("select * from " + table + " where employee_id = ? order by effective_from_date, revision_number", stored(employee));
    }

    /** The current facts: rows nobody has superseded, oldest first. */
    protected List<Map<String, Object>> currentVersions(String table, UUID employee) throws SQLException {
        return rows("select * from " + table + " where employee_id = ? and superseded_at is null order by effective_from_date", stored(employee));
    }

    /** The row of the list in force on the day (from inclusive, to exclusive), or null. */
    protected static Map<String, Object> inForceOn(List<Map<String, Object>> current, LocalDate day) {
        Map<String, Object> found = null;
        for (Map<String, Object> row : current) {
            LocalDate to = (LocalDate) row.get("effective_to_date");
            if (!day.isBefore((LocalDate) row.get("effective_from_date")) && (to == null || day.isBefore(to))) {
                assertThat(found).as("two current rows cover " + day).isNull();
                found = row;
            }
        }
        return found;
    }

    /** W2 section 2: current rows follow each other without gap or overlap and the last one is open. */
    protected static void assertContiguous(List<Map<String, Object>> current) {
        assertThat(current).isNotEmpty();
        for (int i = 1; i < current.size(); i++) {
            assertThat(current.get(i - 1).get("effective_to_date")).as("row " + (i - 1) + " ends where row " + i + " starts")
                    .isEqualTo(current.get(i).get("effective_from_date"));
        }
        assertThat(current.get(current.size() - 1).get("effective_to_date")).as("the last current row is open").isNull();
    }

    /** The state_transitions rows of a personnel command or separation hold, in order. */
    protected List<Map<String, Object>> transitions(String aggregateType, UUID aggregateId) throws SQLException {
        return rows("select * from " + STATE_TRANSITIONS + " where aggregate_type_code = ? and aggregate_id = ? order by transition_sequence_number",
                aggregateType, aggregateId);
    }

    /** ADR-0068 item 1.4: the rows chain (each starts where the last ended) and every step is one the lifecycle allows. */
    protected void assertLawfulLifecycle(List<Map<String, Object>> steps, String lastStatus) {
        // HELD -> PENDING: a release before the effective date (one-design ruling on #732, 2026-10-30; card #931).
        Map<String, List<String>> allowed = Map.of(PENDING, List.of(EFFECTIVE, WITHDRAWN, HELD), HELD, List.of(EFFECTIVE, WITHDRAWN, PENDING));
        assertThat(steps).isNotEmpty();
        String previous = null;
        long sequence = -1;
        for (Map<String, Object> step : steps) {
            String from = (String) step.get("from_status");
            String to = (String) step.get("to_status");
            assertThat(from).as("a step starts where the last one ended").isEqualTo(previous);
            if (from != null) {
                assertThat(allowed.getOrDefault(from, List.of())).as(from + " -> " + to).contains(to);
            } else {
                assertThat(to).as("a command is first PENDING or goes straight to a final state of its entry").isIn(PENDING, EFFECTIVE);
            }
            long next = (long) step.get("transition_sequence_number");
            assertThat(next).isGreaterThan(sequence);
            sequence = next;
            previous = to;
        }
        assertThat(previous).isEqualTo(lastStatus);
    }

    /**
     * Distinct published events of the type that mention the command (by envelope event id: the registry keeps one row per listener, so a
     * new subscriber must not change the count). They are written in the publishing transaction, so nothing is awaited.
     */
    protected int eventsOf(String eventType, UUID command) throws SQLException {
        java.util.Set<String> events = new java.util.HashSet<>();
        java.util.regex.Pattern eventId = java.util.regex.Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"");
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                for (Map<String, Object> row : query(registry, "select serialized_event from " + table + " where event_type like ? and serialized_event like ?",
                        "%." + eventType, "%" + command + "%")) {
                    String json = (String) row.get("serialized_event");
                    java.util.regex.Matcher found = eventId.matcher(json);
                    events.add(found.find() ? found.group(1) : json);
                }
            }
        }
        return events.size();
    }

    protected int allCommandEventsOf(UUID command) throws SQLException {
        int count = 0;
        for (String type : ALL_COMMAND_EVENTS) {
            count += eventsOf(type, command);
        }
        return count;
    }

    protected int auditCount(String action) throws Exception {
        return (int) auditedActions(action).stream().filter(action::equals).count();
    }

    // ---- refusals ----

    /** A refusal of the caller's rights, whichever way the platform signals it. */
    protected static boolean isPermissionRefusal(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AccessDeniedException || cause instanceof AuthenticationException) {
                return true;
            }
            if (cause instanceof DomainException domain && (domain.kind() == ErrorKind.FORBIDDEN || domain.kind() == ErrorKind.UNAUTHENTICATED)) {
                return true;
            }
        }
        return false;
    }

    // ---- jdbc ----

    protected static List<Map<String, Object>> query(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData meta = result.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                        String type = meta.getColumnTypeName(c);
                        Object value = switch (type) {
                            case "timestamptz" -> {
                                OffsetDateTime at = result.getObject(c, OffsetDateTime.class);
                                yield at == null ? null : at.toInstant();
                            }
                            case "date" -> result.getObject(c, LocalDate.class);
                            default -> result.getObject(c);
                        };
                        row.put(meta.getColumnLabel(c).toLowerCase(java.util.Locale.ROOT), value);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    protected static void update(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    protected static Instant instantOf(Object value) {
        return (Instant) value;
    }
}
