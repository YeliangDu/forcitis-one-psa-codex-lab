package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import java.util.UUID;
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
 */
public abstract class AccountsAcceptanceSupport extends AcceptanceSupport {

    public static final String WECOM_LOGIN = "/api/v1/platform/sessions:wecom-login";
    public static final Pattern CANONICAL_UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final AtomicLong EMPLOYEES = new AtomicLong(81_000);
    private static final AtomicLong ORG_UNITS = new AtomicLong(61_000);
    private static final AtomicLong CODES = new AtomicLong();

    /** An account as the login answered it, the employee behind it and the session cookie of that login. */
    protected record SignedIn(UUID userId, long employeeId, String session) {
    }

    protected static long newEmployeeId() {
        return EMPLOYEES.incrementAndGet();
    }

    protected static long newOrgUnitId() {
        return ORG_UNITS.incrementAndGet();
    }

    /** A legal entity code not used before in this run: the prefix and two letters. */
    protected static String newLegalEntityCode(String prefix) {
        long n = CODES.incrementAndGet();
        return prefix + (char) ('A' + (n / 26) % 26) + (char) ('A' + n % 26);
    }

    protected static String wecomUserIdOf(long employeeId) {
        return "wx-acceptance-" + employeeId;
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

    protected void orgUnit(long orgUnitId, Long parentOrgUnitId, Long headEmployeeId) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement insert = workforce.prepareStatement(
                "insert into org_units (id, name, status, parent_org_unit_id, head_employee_id) values (?, ?, 'ACTIVE', ?, ?)")) {
            insert.setLong(1, orgUnitId);
            insert.setString(2, "Synthetic unit " + orgUnitId);
            insert.setObject(3, parentOrgUnitId);
            insert.setObject(4, headEmployeeId);
            insert.executeUpdate();
        }
    }

    protected void employee(long employeeId, long orgUnitId, Long reportsToEmployeeId) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            try (PreparedStatement insert = workforce.prepareStatement("""
                    insert into employees (id, employee_code, display_name, wecom_user_id, employment_status)
                    values (?, ?, ?, ?, 'EMPLOYED')""")) {
                insert.setLong(1, employeeId);
                insert.setString(2, "ACC" + employeeId);
                insert.setString(3, "Synthetic person " + employeeId);
                insert.setString(4, wecomUserIdOf(employeeId));
                insert.executeUpdate();
            }
            try (PreparedStatement insert = workforce.prepareStatement(
                    "insert into assignments (employee_id, org_unit_id, reports_to_employee_id) values (?, ?, ?)")) {
                insert.setLong(1, employeeId);
                insert.setLong(2, orgUnitId);
                insert.setObject(3, reportsToEmployeeId);
                insert.executeUpdate();
            }
        }
    }

    /** Takes the person out of the organisation snapshot, as if the source no longer listed them. */
    protected void removeFromSnapshot(long employeeId) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            for (String table : List.of("assignments", "employees")) {
                try (PreparedStatement delete = workforce.prepareStatement("delete from " + table + " where " + (table.equals("employees") ? "id" : "employee_id") + " = ?")) {
                    delete.setLong(1, employeeId);
                    delete.executeUpdate();
                }
            }
        }
    }

    protected void syncRun(String status, Instant startedAt, Instant finishedAt) throws SQLException {
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

    /**
     * Signs the person in through WeCom (mini program entry, no OAuth state), which opens their account if they have none, and returns
     * the account id the login answered.
     */
    protected SignedIn signIn(long employeeId) throws Exception {
        return signInWith(wecomCodeFor(employeeId), employeeId);
    }

    /** A WeCom code that WeCom answers with the person's WeCom user id; stubbed on the calling thread (Mockito stubbing is not thread-safe). */
    protected String wecomCodeFor(long employeeId) throws ReflectiveOperationException {
        String code = "acceptance-code-" + employeeId + "-" + CODES.incrementAndGet();
        Object wecomUserId = wecomUserId(wecomUserIdOf(employeeId));
        when(wecomGateway.resolveWecomUserId(eq(code), any())).thenAnswer(call -> Optional.of(wecomUserId));
        return code;
    }

    protected SignedIn signInWith(String code, long employeeId) throws Exception {
        MockHttpServletResponse login = mvc.perform(post(WECOM_LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"entry\":\"MINI_PROGRAM\"}")).andReturn().getResponse();
        if (login.getStatus() != 200) {
            throw new AssertionError("the WeCom login of employee " + employeeId + " answered " + login.getStatus() + ": " + login.getContentAsString());
        }
        Object answered = JsonPath.read(login.getContentAsString(), "$.userId");
        String userId = String.valueOf(answered);
        assertThat(userId).as("R-1b P2 ADR-0033 section 3 item 1: the account id is not the employee id").isNotEqualTo(String.valueOf(employeeId));
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
    protected long employeeOfAccount(UUID userId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement select = identity.prepareStatement("select employee_id from user_accounts where user_id = ?")) {
            select.setObject(1, userId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new AssertionError("no account " + userId);
                }
                return rs.getLong(1);
            }
        }
    }

    protected List<UUID> accountsOfEmployee(long employeeId) throws SQLException {
        List<UUID> found = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement select = identity.prepareStatement("select user_id from user_accounts where employee_id = ?")) {
            select.setLong(1, employeeId);
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
    protected void grantByOperations(long employeeId, String roleCode) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int exit = operationsCommands.run(new org.springframework.boot.DefaultApplicationArguments("--operation=grant-role", "--employee-id=" + employeeId,
                "--role-code=" + roleCode, "--operator=acceptance-operator", "--witness=acceptance-witness"),
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
    protected void attributeEmployee(SignedIn maintainer, long employeeId, UUID legalEntity) throws Exception {
        MockHttpServletResponse attributed = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/organization/employee-legal-entities/" + employeeId).header("If-Match", "\"0\"").with(csrf()).with(session(maintainer))
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

    protected void grantRole(UUID userId, String roleCode) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement insert = identity.prepareStatement(
                "insert into account_roles (user_id, role_id) select ?, role_id from roles where code = ? on conflict do nothing")) {
            insert.setObject(1, userId);
            insert.setString(2, roleCode);
            insert.executeUpdate();
        }
    }
}
