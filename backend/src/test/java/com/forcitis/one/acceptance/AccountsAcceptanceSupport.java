package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * People with an employee entry and an account of their own (R-1b P2, ADR-0033 section 3): the employee entry is written in the
 * workforce schema, the account is opened by identity itself when the person signs in through WeCom, and the account id is what the
 * login answers. A login whose answer is not a lower-case canonical UUID, or that equals the employee id, fails the test here: until P2
 * the account id is the employee id.
 * <p>
 * Employee and org unit ids are UUIDs (R-1b P3b, ADR-0033 section 1; RULING-P3B-1). A build whose EmployeeId still wraps a number
 * stores each of them under a number of its own ({@link #UUID_IDS}), so the tests run the same before and after P3b; nothing in a test
 * may depend on that number.
 */
public abstract class AccountsAcceptanceSupport extends AcceptanceSupport {

    public static final String WECOM_LOGIN = "/api/v1/platform/sessions:wecom-login";
    public static final Pattern CANONICAL_UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final AtomicLong EMPLOYEES = new AtomicLong(81_000);
    private static final AtomicLong ORG_UNITS = new AtomicLong(61_000);
    private static final AtomicLong CODES = new AtomicLong();

    /** True once EmployeeId wraps a UUID (R-1b P3b); before that the schema stores the number registered for each UUID. */
    protected static final boolean UUID_IDS = EmployeeId.class.getRecordComponents()[0].getType() == UUID.class;

    private static final Map<UUID, Long> NUMBERS = new ConcurrentHashMap<>();
    private static final Map<Long, UUID> BY_NUMBER = new ConcurrentHashMap<>();

    /** An account as the login answered it, the employee behind it and the session cookie of that login. */
    protected record SignedIn(UUID userId, UUID employeeId, String session) {
    }

    protected static UUID newEmployeeId() {
        return newId(EMPLOYEES);
    }

    protected static UUID newOrgUnitId() {
        return newId(ORG_UNITS);
    }

    /** An employee or org unit id made elsewhere (an own id the source references allocated), registered so the helpers can store it. */
    protected static UUID adoptId(UUID id) {
        NUMBERS.computeIfAbsent(id, adopted -> {
            long number = EMPLOYEES.incrementAndGet();
            BY_NUMBER.put(number, adopted);
            return number;
        });
        return id;
    }

    private static UUID newId(AtomicLong numbers) {
        UUID id = Uuids.timeOrdered(NOW);
        long number = numbers.incrementAndGet();
        NUMBERS.put(id, number);
        BY_NUMBER.put(number, id);
        return id;
    }

    /** The id as the schema and the build take it: the UUID, or before P3b the number registered for it. */
    protected static Object stored(UUID id) {
        if (id == null) {
            return null;
        }
        if (UUID_IDS) {
            return id;
        }
        return serialOf(id);
    }

    /** The id as text in a path, a command argument or a form field. */
    protected static String text(UUID id) {
        return String.valueOf(stored(id));
    }

    /** The id read back from the schema, in either form. */
    protected static UUID fromStored(Object value) {
        if (value == null || value instanceof UUID) {
            return (UUID) value;
        }
        UUID id = BY_NUMBER.get(((Number) value).longValue());
        if (id == null) {
            throw new AssertionError("the schema holds id " + value + ", which this run did not make");
        }
        return id;
    }

    /** The kernel EmployeeId of the employee, built for whichever form the build has. */
    protected static EmployeeId employeeIdOf(UUID id) {
        try {
            Class<?> type = EmployeeId.class.getRecordComponents()[0].getType();
            return EmployeeId.class.getDeclaredConstructor(type).newInstance(stored(id));
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    /** A short number of the id, stable in this run, for the codes, names and WeCom user ids of synthetic people; never an id. */
    protected static long serialOf(UUID id) {
        Long number = NUMBERS.get(id);
        if (number == null) {
            throw new AssertionError("no number registered for " + id + ": make ids with newEmployeeId() or newOrgUnitId()");
        }
        return number;
    }

    /** A legal entity code not used before in this run: the prefix and two letters. */
    protected static String newLegalEntityCode(String prefix) {
        long n = CODES.incrementAndGet();
        return prefix + (char) ('A' + (n / 26) % 26) + (char) ('A' + n % 26);
    }

    /** True once DocumentId wraps a UUID and the approval tables name the document by document_id and hang the child tables on the request (R-1b P4, RULING-P4-1). */
    protected static final boolean DOCUMENT_IDS_ARE_UUIDS = DocumentId.class.getRecordComponents()[0].getType() == UUID.class;

    /** A document id not used before: a UUID once DocumentId wraps one, before that the text "acceptance-" and a UUID. */
    protected static DocumentId newDocumentId() {
        try {
            Class<?> type = DocumentId.class.getRecordComponents()[0].getType();
            UUID id = UUID.randomUUID();
            return DocumentId.class.getDeclaredConstructor(type).newInstance(type == UUID.class ? id : "acceptance-" + id);
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    /** Rows of an approval table that belong to the document, read in whichever form the build has (RULING-P4-1). */
    protected int approvalRowsOf(String table, DocumentReference document) throws SQLException {
        String sql;
        Object key;
        if (!DOCUMENT_IDS_ARE_UUIDS) {
            sql = "select count(*) from " + table + " where document_key = ?";
            key = document.documentId().toString();
        } else if (table.equals("approval_requests")) {
            sql = "select count(*) from approval_requests where document_id = ?";
            key = UUID.fromString(document.documentId().toString());
        } else {
            sql = "select count(*) from " + table + " t join approval_requests r on r.id = t.approval_request_id where r.document_id = ?";
            key = UUID.fromString(document.documentId().toString());
        }
        try (Connection approval = schemaConnection("platform_approval"); PreparedStatement count = approval.prepareStatement(sql)) {
            count.setObject(1, key);
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** The ids (as text) of the document's tasks that wait to be claimed. */
    protected List<String> pendingTaskIdsOf(DocumentReference document) throws SQLException {
        String sql = DOCUMENT_IDS_ARE_UUIDS
                ? "select t.task_id from approval_tasks t join approval_requests r on r.id = t.approval_request_id where r.document_id = ? and t.status = 'PENDING'"
                : "select task_id from approval_tasks where document_key = ? and status = 'PENDING'";
        List<String> tasks = new ArrayList<>();
        try (Connection approval = schemaConnection("platform_approval"); PreparedStatement select = approval.prepareStatement(sql)) {
            select.setObject(1, DOCUMENT_IDS_ARE_UUIDS ? UUID.fromString(document.documentId().toString()) : document.documentId().toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    tasks.add(rs.getString(1));
                }
            }
        }
        return tasks;
    }

    protected static String wecomUserIdOf(UUID employeeId) {
        return "wx-acceptance-" + serialOf(employeeId);
    }

    /**
     * A caller with any session principal name, also one that is not an account id, and permissions given directly: only for
     * SessionPrincipalIT, which sends broken principals on purpose. Everyone else acts in the session of a login (RULING-P2-11).
     */
    protected static RequestPostProcessor principal(String name, String... permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return SecurityMockMvcRequestPostProcessors.user(name).authorities(authorities);
    }

    protected static UUID canonicalUuid(String text, String what) {
        if (text == null || !CANONICAL_UUID.matcher(text).matches()) {
            throw new AssertionError("R-1b P2 ADR-0033 section 6 item 2: " + what + " must be a lower-case canonical UUID, found " + text);
        }
        return UUID.fromString(text);
    }

    protected void orgUnit(UUID orgUnitId, UUID parentOrgUnitId, UUID headEmployeeId) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement insert = workforce.prepareStatement(
                "insert into org_units (id, name, status, parent_org_unit_id, head_employee_id) values (?, ?, 'ACTIVE', ?, ?)")) {
            insert.setObject(1, stored(orgUnitId));
            insert.setString(2, "Synthetic unit " + serialOf(orgUnitId));
            insert.setObject(3, stored(parentOrgUnitId));
            insert.setObject(4, stored(headEmployeeId));
            insert.executeUpdate();
        }
    }

    protected void employee(UUID employeeId, UUID orgUnitId, UUID reportsToEmployeeId) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            try (PreparedStatement insert = workforce.prepareStatement("""
                    insert into employees (id, employee_code, display_name, wecom_user_id, employment_status)
                    values (?, ?, ?, ?, 'EMPLOYED')""")) {
                insert.setObject(1, stored(employeeId));
                insert.setString(2, "ACC" + serialOf(employeeId));
                insert.setString(3, "Synthetic person " + serialOf(employeeId));
                insert.setString(4, wecomUserIdOf(employeeId));
                insert.executeUpdate();
            }
            try (PreparedStatement insert = workforce.prepareStatement(
                    "insert into assignments (employee_id, org_unit_id, reports_to_employee_id) values (?, ?, ?)")) {
                insert.setObject(1, stored(employeeId));
                insert.setObject(2, stored(orgUnitId));
                insert.setObject(3, stored(reportsToEmployeeId));
                insert.executeUpdate();
            }
        }
    }

    /** DR-TB01-FU1: the operator and the witness of an operations command are employees that exist in the organisation snapshot. */
    protected static final String OPERATOR_ID = "01900000-0000-7000-8000-00000000a001";
    protected static final String WITNESS_ID = "01900000-0000-7000-8000-00000000a002";

    /**
     * Makes sure the operator and the witness exist as employees (with an assignment in a unit of their own, because the directory lookup joins it; no WeCom id, so no other lookup sees them); safe to call
     * again, and again after a test cleared the snapshot. Call it before every operations command.
     */
    protected void ensureAttestors() {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement insert = workforce.prepareStatement("""
                insert into employees (id, employee_code, display_name, employment_status) values (?::uuid, ?, ?, 'EMPLOYED')
                on conflict (id) do nothing""")) {
            String unit = "01900000-0000-7000-8000-00000000b001";
            try (PreparedStatement orgUnit = workforce.prepareStatement(
                    "insert into org_units (id, name, status) values (?::uuid, 'Synthetic attestor unit', 'ACTIVE') on conflict (id) do nothing")) {
                orgUnit.setString(1, unit);
                orgUnit.executeUpdate();
            }
            String[] ids = {OPERATOR_ID, WITNESS_ID};
            String[] codes = {"ATT-A001", "ATT-A002"};
            for (int i = 0; i < ids.length; i++) {
                insert.setString(1, ids[i]);
                insert.setString(2, codes[i]);
                insert.setString(3, "Synthetic attestor " + codes[i]);
                insert.executeUpdate();
                try (PreparedStatement assignment = workforce.prepareStatement(
                        "insert into assignments (employee_id, org_unit_id) values (?::uuid, ?::uuid) on conflict (employee_id) do nothing")) {
                    assignment.setString(1, ids[i]);
                    assignment.setString(2, unit);
                    assignment.executeUpdate();
                }
            }
        } catch (SQLException failure) {
            throw new AssertionError("could not make the operator and the witness exist", failure);
        }
    }

    /** Takes the person out of the organisation snapshot, as if the source no longer listed them. */
    protected void removeFromSnapshot(UUID employeeId) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            for (String table : List.of("assignments", "employees")) {
                try (PreparedStatement delete = workforce.prepareStatement("delete from " + table + " where " + (table.equals("employees") ? "id" : "employee_id") + " = ?")) {
                    delete.setObject(1, stored(employeeId));
                    delete.executeUpdate();
                }
            }
        }
    }

    /**
     * A row of the old sync run log, written only while that table exists: #934 (ADR-0068 section 2.5 D2) drops sync_runs, after which
     * no run is recorded and this does nothing. Callers use it to put the snapshot in the state a recent run left it in.
     */
    protected void syncRun(String status, Instant startedAt, Instant finishedAt) throws SQLException {
        if (!oldSyncTablesExist()) {
            return;
        }
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement insert = workforce.prepareStatement("""
                insert into sync_runs (id, trigger_kind, status, started_at, finished_at, failure_reason)
                values (?, 'SCHEDULED', ?, ?, ?, ?)""")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, status);
            insert.setTimestamp(3, Timestamp.from(startedAt));
            insert.setTimestamp(4, finishedAt == null ? null : Timestamp.from(finishedAt));
            insert.setString(5, "FAILED".equals(status) ? "synthetic: the organisation source did not answer" : null);
            insert.executeUpdate();
        }
    }

    /** True while organization_workforce.sync_runs exists (until #934 drops it with the other tables of the old sync). */
    protected boolean oldSyncTablesExist() throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce");
             PreparedStatement select = workforce.prepareStatement("select to_regclass('organization_workforce.sync_runs') is not null");
             ResultSet rs = select.executeQuery()) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    /**
     * Signs the person in through WeCom (mini program entry, no OAuth state), which opens their account if they have none, and returns
     * the account id the login answered.
     */
    protected SignedIn signIn(UUID employeeId) throws Exception {
        return signInWith(wecomCodeFor(employeeId), employeeId);
    }

    /** A WeCom code that WeCom answers with the person's WeCom user id; stubbed on the calling thread (Mockito stubbing is not thread-safe). */
    protected String wecomCodeFor(UUID employeeId) throws ReflectiveOperationException {
        String code = "acceptance-code-" + serialOf(employeeId) + "-" + CODES.incrementAndGet();
        Object wecomUserId = wecomUserId(wecomUserIdOf(employeeId));
        when(wecomGateway.resolveWecomUserId(eq(code), any())).thenAnswer(call -> Optional.of(wecomUserId));
        return code;
    }

    protected SignedIn signInWith(String code, UUID employeeId) throws Exception {
        MockHttpServletResponse login = mvc.perform(post(WECOM_LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"entry\":\"MINI_PROGRAM\"}")).andReturn().getResponse();
        if (login.getStatus() != 200) {
            throw new AssertionError("the WeCom login of employee " + employeeId + " answered " + login.getStatus() + ": " + login.getContentAsString());
        }
        Object answered = JsonPath.read(login.getContentAsString(), "$.userId");
        String userId = String.valueOf(answered);
        assertThat(userId).as("R-1b P2 ADR-0033 section 3 item 1: the account id is not the employee id").isNotEqualTo(text(employeeId));
        return new SignedIn(canonicalUuid(userId, "the userId the login answers"), employeeId, sessionCookie(login));
    }

    private static String sessionCookie(MockHttpServletResponse response) {
        for (String header : response.getHeaders("Set-Cookie")) {
            if (header.startsWith("SESSION=")) {
                int end = header.indexOf(';');
                return header.substring("SESSION=".length(), end < 0 ? header.length() : end);
            }
        }
        return null;
    }

    /** The WeCom user id type is looked up from the gateway, so that this support does not depend on the package that holds it. */
    private static Object wecomUserId(String value) throws ReflectiveOperationException {
        Method resolve = Arrays.stream(WecomGateway.class.getMethods()).filter(m -> m.getName().equals("resolveWecomUserId")).findFirst().orElseThrow();
        Class<?> type = (Class<?>) ((ParameterizedType) resolve.getGenericReturnType()).getActualTypeArguments()[0];
        return type.getDeclaredConstructor(String.class).newInstance(value);
    }

    /** The employee id the account row of identity names; fails while the account table has no employee column. */
    protected UUID employeeOfAccount(UUID userId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement select = identity.prepareStatement("select employee_id from user_accounts where user_id = ?")) {
            select.setObject(1, userId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("no account " + userId);
                }
                return fromStored(rs.getObject(1));
            }
        }
    }

    protected List<UUID> accountsOfEmployee(UUID employeeId) throws SQLException {
        List<UUID> found = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement select = identity.prepareStatement("select user_id from user_accounts where employee_id = ?")) {
            select.setObject(1, stored(employeeId));
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    found.add(rs.getObject(1, UUID.class));
                }
            }
        }
        return found;
    }

    /**
     * The audit entries of one actor, found with the actorId query parameter (a UUID since P2), whose action starts with the prefix;
     * waits up to 15 s for at least one, since entries that come from domain events are written after the commit.
     */
    protected List<java.util.Map<String, Object>> auditEntriesBy(UUID actor, String actionPrefix) {
        return org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).pollInterval(java.time.Duration.ofMillis(100))
                .until(() -> auditQuery("actorId", actor.toString(), actionPrefix), found -> !found.isEmpty());
    }

    /** The audit entries whose action starts with the prefix and, when parameter is not null, that match one more query parameter. */
    protected List<java.util.Map<String, Object>> auditQuery(String parameter, String value, String actionPrefix) throws Exception {
        var query = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(AUDIT_ENTRIES).param("actionPrefix", actionPrefix)
                .param("limit", "200").with(AUDIT_READER);
        if (parameter != null) {
            query = query.param(parameter, value);
        }
        MockHttpServletResponse found = mvc.perform(query).andReturn().getResponse();
        if (found.getStatus() != 200) {
            throw new AssertionError("the audit query by " + parameter + " " + value + " answered " + found.getStatus() + ": " + found.getContentAsString());
        }
        return JsonPath.read(found.getContentAsString(), "$.items");
    }

    /**
     * The production session of a login (RULING-P2-8): the SESSION cookie that {@link #signIn} kept, sent with the request, so that the
     * session filter resolves the principal, its employee and its permissions from the account's roles.
     */
    protected static RequestPostProcessor session(SignedIn signedIn) {
        return request -> {
            List<jakarta.servlet.http.Cookie> cookies = new ArrayList<>(request.getCookies() == null ? List.of() : Arrays.asList(request.getCookies()));
            cookies.removeIf(cookie -> cookie.getName().equals("SESSION"));
            cookies.add(new jakarta.servlet.http.Cookie("SESSION", signedIn.session()));
            request.setCookies(cookies.toArray(jakarta.servlet.http.Cookie[]::new));
            return request;
        };
    }

    @Autowired
    private org.springframework.session.FindByIndexNameSessionRepository<? extends org.springframework.session.Session> sessionRepository;

    @Autowired
    private com.forcitis.one.platform.identity.application.OperationsCommands operationsCommands;

    /**
     * Use cases without an HTTP entry (the timesheet module's) run with the security context the login stored in the session, read back
     * from the Spring Session repository; a context that is not the production session authentication stops the test. {@link #signOut()}
     * clears it.
     */
    protected void actInSession(SignedIn signedIn) {
        String sessionId = new String(java.util.Base64.getDecoder().decode(signedIn.session()), java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.session.Session stored = sessionRepository.findById(sessionId);
        if (stored == null) {
            throw new AssertionError("no session " + sessionId + " in the session repository");
        }
        Object context = stored.getAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(context instanceof org.springframework.security.core.context.SecurityContext securityContext) || securityContext.getAuthentication() == null
                || !securityContext.getAuthentication().getClass().getSimpleName().equals("IdentityAuthentication")) {
            throw new AssertionError("RULING-P2-8: the session of the login does not hold the production session authentication, found " + context);
        }
        org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
    }

    @org.junit.jupiter.api.AfterEach
    protected void signOut() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /**
     * Grants a role with the operations command (--employee-id), which opens the account of an employed person who has none; done before
     * the person signs in, so the session starts with the role.
     */
    protected void grantByOperations(UUID employeeId, String roleCode) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        ensureAttestors();
        int exit = operationsCommands.run(new org.springframework.boot.DefaultApplicationArguments("--operation=grant-role", "--employee-id=" + text(employeeId),
                "--role-code=" + roleCode, "--operator=01900000-0000-7000-8000-00000000a001", "--witness=01900000-0000-7000-8000-00000000a002"),
                new java.io.PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8), new java.io.PrintStream(err, true, java.nio.charset.StandardCharsets.UTF_8));
        if (exit != 0) {
            throw new AssertionError("grant-role " + roleCode + " to employee " + employeeId + " exited " + exit + ": " + err.toString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * The session asks for the administrator mode, proving the person again with a fresh WeCom code through the workbench entry and its
     * one-time state; answers the sign-in with the session the mode lives in.
     */
    protected SignedIn administratorMode(SignedIn signedIn) throws Exception {
        MockHttpServletResponse issued = mvc.perform(post("/api/v1/platform/login-states").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entry\":\"WORKBENCH\"}")).andReturn().getResponse();
        if (issued.getStatus() != 200) {
            throw new AssertionError("asking for a login state answered " + issued.getStatus() + ": " + issued.getContentAsString());
        }
        String state = JsonPath.read(issued.getContentAsString(), "$.state");
        String binding = cookieValue(issued, "loginBinding");
        String code = wecomCodeFor(signedIn.employeeId());
        MockHttpServletResponse entered = mvc.perform(post("/api/v1/platform/sessions:enter-administrator-mode").with(csrf()).with(session(signedIn))
                .cookie(new jakarta.servlet.http.Cookie("loginBinding", binding)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}")).andReturn().getResponse();
        if (entered.getStatus() != 200) {
            throw new AssertionError("entering the administrator mode answered " + entered.getStatus() + ": " + entered.getContentAsString());
        }
        String renewed = cookieValue(entered, "SESSION");
        return new SignedIn(signedIn.userId(), signedIn.employeeId(), renewed == null ? signedIn.session() : renewed);
    }

    private static String cookieValue(MockHttpServletResponse response, String name) {
        for (String header : response.getHeaders("Set-Cookie")) {
            if (header.startsWith(name + "=")) {
                int end = header.indexOf(';');
                return header.substring(name.length() + 1, end < 0 ? header.length() : end);
            }
        }
        return null;
    }

    /** A legal entity created by a maintainer in their own session. */
    protected UUID createLegalEntity(SignedIn maintainer, String code, String name) throws Exception {
        MockHttpServletResponse created = mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(session(maintainer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}")).andReturn().getResponse();
        if (created.getStatus() != 201) {
            throw new AssertionError("creating legal entity " + code + " answered " + created.getStatus() + ": " + created.getContentAsString());
        }
        return UUID.fromString(JsonPath.read(created.getContentAsString(), "$.legalEntityId"));
    }

    /** The year of the fixed clock gets a plain Monday-to-Friday calendar if it has none, maintained in the maintainer's session. */
    protected void maintainWorkCalendar(SignedIn maintainer) throws Exception {
        String year = "/api/v1/organization/work-calendars/" + TODAY.getYear();
        if (mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(year).with(session(maintainer))).andReturn().getResponse().getStatus() == 200) {
            return;
        }
        MockHttpServletResponse maintained = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(year).header("If-Match", "\"0\"")
                .with(csrf()).with(session(maintainer)).contentType(MediaType.APPLICATION_JSON).content("{\"days\":[],\"lockedDaysConfirmed\":true}")).andReturn().getResponse();
        if (maintained.getStatus() != 200) {
            throw new AssertionError("maintaining the work calendar answered " + maintained.getStatus() + ": " + maintained.getContentAsString());
        }
    }

    /** Attributes the employee to the legal entity from 30 days before the fixed today on, done by a maintainer who is someone else. */
    protected void attributeEmployee(SignedIn maintainer, UUID employeeId, UUID legalEntity) throws Exception {
        MockHttpServletResponse attributed = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/organization/employee-legal-entities/" + text(employeeId)).header("If-Match", "\"0\"").with(csrf()).with(session(maintainer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":\"" + legalEntity + "\",\"effectiveFrom\":\"" + TODAY.minusDays(30) + "\"}"))
                .andReturn().getResponse();
        if (attributed.getStatus() != 200) {
            throw new AssertionError("attributing employee " + employeeId + " answered " + attributed.getStatus() + ": " + attributed.getContentAsString());
        }
    }

    /** Puts the seeded numbering template of the document type in force, confirmed in the session of someone who did not write it. */
    protected void confirmSeededNumberingRule(String documentType, SignedIn approver) throws Exception {
        String rule = "/api/v1/platform/numbering-rules/" + documentType;
        MockHttpServletResponse read = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(rule).with(session(approver)))
                .andReturn().getResponse();
        if (read.getStatus() != 200) {
            throw new AssertionError("reading the " + documentType + " template answered " + read.getStatus() + ": " + read.getContentAsString());
        }
        if (!read.getContentAsString().contains("\"pending\":{")) {
            return;
        }
        int version = JsonPath.read(read.getContentAsString(), "$.pending.templateVersion");
        MockHttpServletResponse confirmed = mvc.perform(post(rule + ":confirm").with(csrf()).with(session(approver)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateVersion\":" + version + "}")).andReturn().getResponse();
        if (confirmed.getStatus() != 200) {
            throw new AssertionError("confirming the " + documentType + " template answered " + confirmed.getStatus() + ": " + confirmed.getContentAsString());
        }
    }

    /**
     * Gives the account an open grant of the role the way a fixture may (channel OPERATIONS, no grant person), skipping a role the account already
     * holds open; the grant store is account_role_grants (ADR-0034 decision 2, #732).
     */
    protected void grantRole(UUID userId, String roleCode) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement insert = identity.prepareStatement("""
                insert into account_role_grants (account_role_grant_id, user_id, role_id, effective_from_at, grant_channel_kind, grant_audit_entry_id, created_at, updated_at)
                select uuidv7(), ?, role_id, ?, 'OPERATIONS', uuidv7(), ?, ? from roles where code = ? on conflict do nothing""")) {
            java.time.OffsetDateTime at = java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
            insert.setObject(1, userId);
            insert.setObject(2, at);
            insert.setObject(3, at);
            insert.setObject(4, at);
            insert.setString(5, roleCode);
            insert.executeUpdate();
        }
    }
}
