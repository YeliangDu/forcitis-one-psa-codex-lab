package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Ground of the F-1 data scope tests. The organisation is written into the workforce tables once per class ({@link #seedOrganisation()});
 * until #934 it came in through the sync, which is gone. People sign in through the real WeCom login (the gateway answers the code);
 * the first administrators get super_admin from the operations command and act in the administrator mode, as ADR-0026 section 5 and
 * ADR-0027 section 2 item 5 require. Own ids are opaque strings read back from HTTP answers; the numbers in here are source ids only.
 * Contracts that do not exist yet are listed in platform/identity/F1_ACCEPTANCE.md.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DataScopeSupport extends AccountsAcceptanceSupport {

    public static final String ORG_UNITS = "/api/v1/organization/org-units";
    public static final String EMPLOYEES = "/api/v1/organization/employees";
    public static final String ROLES = "/api/v1/platform/roles";
    public static final String USER_ACCOUNTS = "/api/v1/platform/user-accounts";
    public static final String AUTHORIZATION_REQUESTS = "/api/v1/platform/authorization-requests";
    public static final String PERMISSION_ADJUSTMENTS = "/api/v1/platform/permission-adjustments";
    public static final String CURRENT_SESSION = "/api/v1/platform/sessions:current";

    public static final String ORG_UNIT_READ = "organization.workforce:org-unit:read";
    public static final String EMPLOYEE_READ = "organization.workforce:employee:read";
    public static final String NUMBERING_RULE_READ = "platform.numbering:numbering-rule:read";
    public static final String AUDIT_ENTRY_READ = "platform.audit:audit-entry:read";
    public static final String AUDIT_ENTRY_EXPORT = "platform.audit:audit-entry:export";

    public static final String EMPLOYEE_ROLE = "employee";
    public static final String SUPER_ADMIN = "super_admin";
    public static final String ORG_DATA_ADMIN = "org_data_admin";
    public static final String SECURITY_AUDITOR = "security_auditor";
    public static final String BUSINESS_ADMIN = "business_admin";

    private static final Duration ASYNC_LIMIT = Duration.ofSeconds(20);

    /** The organisation source stays a double, so that no login of this ground ever reaches a real source. */
    @MockitoBean
    protected OrgSource orgSource;

    @Autowired
    private OperationsCommands operations;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /** The operations command names the person by employee id, which no HTTP answer carries yet; the module api gives it. */
    @Autowired
    private OrgDirectory directory;

    private final AtomicLong codes = new AtomicLong();
    private final Map<String, Unit> units = new LinkedHashMap<>();
    private final Map<String, Post> positions = new LinkedHashMap<>();
    private final Map<String, Person> people = new LinkedHashMap<>();
    private final Map<String, String> heads = new LinkedHashMap<>();
    private final Map<String, String> userIds = new HashMap<>();
    private final Map<String, String> employeeIds = new HashMap<>();
    private final Map<String, TimedSession> sessions = new HashMap<>();
    private final Set<String> administrators = new java.util.HashSet<>();
    private final Map<String, String> orgUnitIds = new HashMap<>();

    // ---- the organisation ----------------------------------------------------------------------------------------------------

    private record Unit(UUID id, String code, String parentCode, int sortOrder) {
    }

    private record Post(UUID id, String code) {
    }

    private record Person(UUID id, String key, String unitCode, String positionCode) {
    }

    protected void unit(String code, String parentCode) {
        if (parentCode != null) {
            known(units, parentCode);
        }
        units.put(code, new Unit(newOrgUnitId(), code, parentCode, units.size() + 1));
    }

    /** The person heads the unit. */
    protected void head(String unitCode, String key) {
        heads.put(unitCode, key);
    }

    protected static String unitName(String code) {
        return "Synthetic unit " + code;
    }

    protected void position(String code) {
        positions.put(code, new Post(Uuids.timeOrdered(NOW), code));
    }

    protected static String positionName(String code) {
        return "Synthetic post " + code;
    }

    protected void person(String key, String unitCode, String positionCode) {
        known(units, unitCode);
        known(positions, positionCode);
        people.put(key, new Person(newEmployeeId(), key, unitCode, positionCode));
    }

    protected static String employeeCode(String key) {
        return "F1-" + key;
    }

    /**
     * Writes the organisation as it stands in the workforce tables (units, posts, employed people and their assignments, heads) and, while
     * the old sync run log still exists, a run that has just succeeded. Until #934 the picture came in through the sync from the
     * organisation source; with the sync gone (ADR-0055 section 6, ADR-0068 section 2.5) no path is left that brings a whole organisation
     * in, so the fixture writes it the way the other acceptance fixtures write org units and people.
     */
    protected void seedOrganisation() throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            for (Post post : positions.values()) {
                write(workforce, "insert into positions (id, code, name, sort_order, status) values (?, ?, ?, ?, 'ACTIVE')", post.id(), post.code(),
                        positionName(post.code()), 1);
            }
            for (Unit unit : units.values()) {
                write(workforce, "insert into org_units (id, code, name, parent_org_unit_id, sort_order, status) values (?, ?, ?, ?, ?, 'ACTIVE')",
                        stored(unit.id()), unit.code(), unitName(unit.code()), unit.parentCode() == null ? null : stored(known(units, unit.parentCode()).id()),
                        unit.sortOrder());
            }
            for (Person person : people.values()) {
                write(workforce, """
                        insert into employees (id, employee_code, display_name, email, gender, wecom_user_id, employment_status, is_fee_earner)
                        values (?, ?, ?, ?, 'FEMALE', ?, 'EMPLOYED', false)""", stored(person.id()), employeeCode(person.key()), "Synthetic " + person.key(),
                        person.key() + "@example.test", wecomUserId(person.key()));
                write(workforce, "insert into assignments (employee_id, org_unit_id, position_id) values (?, ?, ?)", stored(person.id()),
                        stored(known(units, person.unitCode()).id()), known(positions, person.positionCode()).id());
            }
            for (Map.Entry<String, String> head : heads.entrySet()) {
                write(workforce, "update org_units set head_employee_id = ? where id = ?", stored(known(people, head.getValue()).id()),
                        stored(known(units, head.getKey()).id()));
            }
        }
        syncRun("SUCCEEDED", clock.instant().minusSeconds(120), clock.instant().minusSeconds(60));
    }

    private static void write(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    // ---- sessions ------------------------------------------------------------------------------------------------------------

    private static String wecomUserId(String key) {
        return "f1-wx-" + key;
    }

    /** A fresh ordinary session of the person through the WeCom login; take a new one after the person's roles changed. */
    protected String login(String key) throws Exception {
        LoginState state = loginState();
        String code = "f1-code-" + key + "-" + codes.incrementAndGet();
        when(wecomGateway.resolveWecomUserId(code, WecomEntry.WORKBENCH)).thenReturn(Optional.of(new WecomUserId(wecomUserId(key))));
        MockHttpServletResponse login = mvc.perform(post("/api/v1/platform/sessions:wecom-login").with(csrf()).cookie(new Cookie("loginBinding", state.binding()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\",\"state\":\"" + state.state() + "\",\"entry\":\"WORKBENCH\"}"))
                .andReturn().getResponse();
        require(login, 200, "fixture: WeCom login of " + key);
        String session = cookie(login, "SESSION");
        if (session == null) {
            throw new AssertionError("fixture: the login of " + key + " set no SESSION cookie");
        }
        return session;
    }

    private record LoginState(String state, String binding) {
    }

    private LoginState loginState() throws Exception {
        MockHttpServletResponse issued = mvc.perform(post("/api/v1/platform/login-states").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entry\":\"WORKBENCH\"}")).andReturn().getResponse();
        require(issued, 200, "fixture: login state");
        return new LoginState(JsonPath.read(issued.getContentAsString(), "$.state"), cookie(issued, "loginBinding"));
    }

    protected static RequestPostProcessor as(String session) {
        return request -> {
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            request.setCookies(Stream.concat(Arrays.stream(existing), Stream.of(new Cookie("SESSION", session))).toArray(Cookie[]::new));
            return request;
        };
    }

    /** The account id of the person, read from the session; the person signs in once to get an account. */
    protected String userId(String key) throws Exception {
        String known = userIds.get(key);
        if (known != null) {
            return known;
        }
        MockHttpServletResponse current = mvc.perform(get(CURRENT_SESSION).with(as(login(key)))).andReturn().getResponse();
        require(current, 200, "fixture: current session of " + key);
        String id = JsonPath.read(current.getContentAsString(), "$.userId");
        userIds.put(key, id);
        return id;
    }

    /** The employee id of the person, read once through the workforce api while the person is employed, then kept. */
    protected String employeeIdOf(String key) {
        return employeeIds.computeIfAbsent(key, person -> String.valueOf(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId(wecomUserId(person))))
                .orElseThrow(() -> new AssertionError("fixture: " + person + " is not in the seeded organisation")).employeeId().value()));
    }

    /** The operations command of ADR-0026 section 5 item 5 and ADR-0027 section 1: an individual grant with operator and witness. */
    protected void grantByOperations(String key, String roleCode) throws Exception {
        userId(key);
        String employee = employeeIdOf(key);
        ensureAttestors();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = operations.run(new DefaultApplicationArguments("--operation=grant-role", "--employee-id=" + employee, "--role-code=" + roleCode,
                "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        sessions.remove(key);
        if (exit != 0) {
            throw new AssertionError("fixture: grant-role " + roleCode + " to " + key + " exited " + exit + ": " + err.toString(StandardCharsets.UTF_8));
        }
    }

    private record TimedSession(String session, long startedNanos) {
        boolean fresh() {
            return System.nanoTime() - startedNanos < SESSION_REUSE.toNanos();
        }
    }

    /**
     * The clock of the application is fixed weeks after the real one, and a session is checked against it again once the principal
     * cache (60 s) runs out; a session reused for longer than that is refused. So sessions are reused for a short while only.
     */
    private static final Duration SESSION_REUSE = Duration.ofSeconds(30);

    @BeforeEach
    void noSessionCrossesATest() {
        sessions.clear();
    }

    /** An ordinary session of a person whose roles the test does not change, renewed when it is no longer fresh. */
    protected String session(String key) throws Exception {
        TimedSession known = sessions.get(key);
        if (known != null && known.fresh()) {
            return known.session();
        }
        String session = administrators.contains(key) ? enterAdministratorMode(key) : login(key);
        sessions.put(key, new TimedSession(session, System.nanoTime()));
        return session;
    }

    /** A super_admin holder (granted once by the operations command) in the administrator mode, re-verified with a fresh WeCom code. */
    protected String administrator(String key) throws Exception {
        if (administrators.add(key)) {
            grantByOperations(key, SUPER_ADMIN);
        }
        return session(key);
    }

    private String enterAdministratorMode(String key) throws Exception {
        String session = login(key);
        LoginState state = loginState();
        String code = "f1-reverify-" + key + "-" + codes.incrementAndGet();
        when(wecomGateway.resolveWecomUserId(code, WecomEntry.WORKBENCH)).thenReturn(Optional.of(new WecomUserId(wecomUserId(key))));
        MockHttpServletResponse entered = mvc.perform(post("/api/v1/platform/sessions:enter-administrator-mode").with(csrf()).with(as(session))
                .cookie(new Cookie("loginBinding", state.binding())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"state\":\"" + state.state() + "\",\"entry\":\"WORKBENCH\"}")).andReturn().getResponse();
        require(entered, 200, "fixture: administrator mode of " + key);
        String renewed = cookie(entered, "SESSION");
        return renewed == null ? session : renewed;
    }

    // ---- roles and grants over HTTP ------------------------------------------------------------------------------------------

    /** One org unit of a CUSTOM_ORG_UNITS role, chosen with or without the units below it (user 2026-10-15 via one-coordinator). */
    public record CustomOrgUnit(String orgUnitId, boolean includeDescendants) {
        String json() {
            return "{\"orgUnitId\":\"" + orgUnitId + "\",\"includeDescendants\":" + includeDescendants + "}";
        }
    }

    protected static CustomOrgUnit onlyUnit(String orgUnitId) {
        return new CustomOrgUnit(orgUnitId, false);
    }

    protected static CustomOrgUnit withDescendants(String orgUnitId) {
        return new CustomOrgUnit(orgUnitId, true);
    }

    protected static String roleBody(String code, String accessScope, List<CustomOrgUnit> customOrgUnits, String... permissionCodes) {
        StringBuilder body = new StringBuilder("{\"code\":\"").append(code).append("\",\"name\":\"Synthetic role ").append(code)
                .append("\",\"enabled\":true,\"accessScope\":\"").append(accessScope).append('"');
        if (customOrgUnits != null) {
            body.append(",\"customOrgUnits\":[").append(String.join(",", customOrgUnits.stream().map(CustomOrgUnit::json).toList())).append(']');
        }
        return body.append(",\"permissionCodes\":").append(jsonStrings(List.of(permissionCodes))).append('}').toString();
    }

    protected static String jsonStrings(List<String> values) {
        return values.stream().map(v -> "\"" + v + "\"").reduce((a, b) -> a + "," + b).map(v -> "[" + v + "]").orElse("[]");
    }

    protected MockHttpServletResponse createRole(String administrator, String body) throws Exception {
        return mvc.perform(post(ROLES).with(csrf()).with(as(administrator)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    /** A role that carries no sensitive permission, created at once; fails the test with the answer when the role cannot be made. */
    protected String ordinaryRole(String administrator, String code, String accessScope, List<CustomOrgUnit> customOrgUnits, String... permissionCodes)
            throws Exception {
        MockHttpServletResponse created = createRole(administrator, roleBody(code, accessScope, customOrgUnits, permissionCodes));
        require(created, 201, "creating role " + code);
        return JsonPath.read(created.getContentAsString(), "$.roleId");
    }

    protected MockHttpServletResponse readRole(String administrator, String roleId) throws Exception {
        return mvc.perform(get(ROLES + "/" + roleId).with(as(administrator))).andReturn().getResponse();
    }

    protected MockHttpServletResponse reviseRole(String administrator, String roleId, String body) throws Exception {
        String etag = readRole(administrator, roleId).getHeader("ETag");
        return mvc.perform(put(ROLES + "/" + roleId).header("If-Match", etag).with(csrf()).with(as(administrator))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    /** The role id for a code, read from the role list of the administrator. */
    protected Optional<String> roleIdOf(String administrator, String code) throws Exception {
        MockHttpServletResponse listed = mvc.perform(get(ROLES).param("limit", "200").with(as(administrator))).andReturn().getResponse();
        require(listed, 200, "listing roles");
        for (Map<String, Object> role : items(listed)) {
            if (code.equals(role.get("code"))) {
                return Optional.of(String.valueOf(role.get("roleId")));
            }
        }
        return Optional.empty();
    }

    protected MockHttpServletResponse readAccount(String administrator, String key) throws Exception {
        return mvc.perform(get(USER_ACCOUNTS + "/" + userId(key)).with(as(administrator))).andReturn().getResponse();
    }

    protected List<String> roleCodesOf(String administrator, String key) throws Exception {
        MockHttpServletResponse account = readAccount(administrator, key);
        require(account, 200, "reading the account of " + key);
        return JsonPath.read(account.getContentAsString(), "$.roleCodes");
    }

    /** Account authorisation: the full set after the change is employee plus the given roles (ADR-0034 section 2 item 5). */
    protected MockHttpServletResponse replaceRoles(String administrator, String key, String... roleCodes) throws Exception {
        String account = userId(key);
        sessions.remove(key);
        String etag = readAccount(administrator, key).getHeader("ETag");
        Set<String> wanted = new LinkedHashSet<>();
        wanted.add(EMPLOYEE_ROLE);
        wanted.addAll(List.of(roleCodes));
        return mvc.perform(post(USER_ACCOUNTS + "/" + account + ":replace-roles").header("If-Match", etag).with(csrf()).with(as(administrator))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":" + jsonStrings(new ArrayList<>(wanted)) + "}")).andReturn().getResponse();
    }

    protected void grantOrdinaryRoles(String administrator, String key, String... roleCodes) throws Exception {
        MockHttpServletResponse replaced = replaceRoles(administrator, key, roleCodes);
        require(replaced, 204, "granting " + List.of(roleCodes) + " to " + key);
    }

    protected MockHttpServletResponse confirm(String administrator, String requestId) throws Exception {
        return mvc.perform(post(AUTHORIZATION_REQUESTS + "/" + requestId + ":confirm").with(csrf()).with(as(administrator))).andReturn().getResponse();
    }

    /**
     * Audit entries are partitioned by UTC month and the baseline pre-creates them from 2026-10; a clock earlier than that cannot write its sign-in
     * entry. Creates the month's partition through the schema's own function (a no-op when it exists); the database is the class's own, so nothing is dropped.
     */
    protected void ensureAuditPartition(java.time.LocalDate month) throws Exception {
        try (Connection audit = schemaConnection("platform_audit"); PreparedStatement statement = audit.prepareStatement("select create_audit_entry_partitions(?, ?)")) {
            statement.setObject(1, month);
            statement.setObject(2, month);
            statement.executeQuery().close();
        }
    }

    /** Moves the application clock; sessions taken before do not survive the jump, so they are dropped. */
    protected void advanceTo(Instant instant) {
        clock.set(instant);
        sessions.clear();
    }

    /** The WeCom cards handed to the gateway so far (the gateway is the double; delivery itself is platform.integration's). */
    protected List<WecomCard> sentCards() {
        org.mockito.ArgumentCaptor<WecomCard> cards = org.mockito.ArgumentCaptor.forClass(WecomCard.class);
        org.mockito.Mockito.verify(wecomGateway, org.mockito.Mockito.atLeast(0)).send(cards.capture());
        return cards.getAllValues();
    }

    protected static String wecomUserIdOf(String key) {
        return wecomUserId(key);
    }

    // ---- permission adjustments (ADR-0032 section 4 item 3, section 5) -----------------------------------------------------

    public static final String SENSITIVE_ROLE_REVIEW = "SENSITIVE_ROLE_REVIEW";
    public static final String SCOPE_REVIEW = "SCOPE_REVIEW";

    protected MockHttpServletResponse listAdjustments(String session) throws Exception {
        return mvc.perform(get(PERMISSION_ADJUSTMENTS).param("limit", "200").with(as(session))).andReturn().getResponse();
    }

    /** Every item the session lists; a refusal is not something to wait for, so it fails at once instead of being retried. */
    protected List<Map<String, Object>> adjustments(String session) throws Exception {
        MockHttpServletResponse listed = listAdjustments(session);
        if (listed.getStatus() != 200) {
            throw new IllegalStateException("the permission adjustment list answered " + listed.getStatus() + ": " + listed.getContentAsString());
        }
        return items(listed);
    }

    protected static String adjustmentIdOf(Map<String, Object> item) {
        return String.valueOf(item.get("permissionAdjustmentId"));
    }

    protected MockHttpServletResponse readAdjustment(String session, String adjustmentId) throws Exception {
        return mvc.perform(get(PERMISSION_ADJUSTMENTS + "/" + adjustmentId).with(as(session))).andReturn().getResponse();
    }

    /** F1-02 (user 2026-10-15): a reason, and the business owner who confirmed the need, named by employee id (ADR-0044 draft). */
    protected String upholdBody(String reason, String businessOwnerKey) {
        return "{\"reason\":\"" + reason + "\",\"businessOwnerEmployeeId\":\"" + employeeIdOf(businessOwnerKey) + "\"}";
    }

    /** RULE-387 items and transfer scope reviews: a reason only, no business owner and no expiry (ADR-0044 Q1 (c)). */
    protected static String reasonOnly(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    /** Marks an item adjusted, naming the authorization request that changed the grant (ADR-0044 section 2 item 1). */
    protected MockHttpServletResponse markAdjusted(String session, String adjustmentId, String authorizationRequestId) throws Exception {
        String etag = etagOf(session, adjustmentId);
        var request = post(PERMISSION_ADJUSTMENTS + "/" + adjustmentId + ":mark-adjusted").with(csrf()).with(as(session))
                .contentType(MediaType.APPLICATION_JSON).content("{\"authorizationRequestId\":\"" + authorizationRequestId + "\"}");
        return mvc.perform(etag == null ? request : request.header("If-Match", etag)).andReturn().getResponse();
    }

    /** Audit entries whose action starts with the prefix, read through the audit HTTP query. */
    protected List<Map<String, Object>> auditEntries(String actionPrefix) throws Exception {
        MockHttpServletResponse entries = mvc.perform(get(AUDIT_ENTRIES).param("actionPrefix", actionPrefix).param("limit", "200").with(AUDIT_READER))
                .andReturn().getResponse();
        require(entries, 200, "the audit query");
        return items(entries);
    }

    protected static String auditText(Map<String, Object> entry) {
        return entry.get("detail") + " " + entry.get("changeReason") + " " + entry.get("beforeText") + " " + entry.get("afterText") + " " + entry.get("subjectId");
    }

    /**
     * Runs the uphold expiry once, as the daily job would (ADR-0044 draft: 00:05 Asia/Shanghai, by the injected clock). The assumed entry
     * is a bean of platform.identity whose type, or one of whose interfaces, is named ExpirePermissionAdjustments, with a public
     * no-argument execute() that enters its own work context. Found at run time so this file compiles before it exists; a missing entry
     * fails the test, it is never skipped.
     */
    protected void runUpholdExpiry() {
        for (String name : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(name);
            if (type == null) {
                continue;
            }
            List<Class<?>> candidates = new ArrayList<>(org.springframework.util.ClassUtils.getAllInterfacesForClassAsSet(org.springframework.util.ClassUtils.getUserClass(type)));
            candidates.addFirst(org.springframework.util.ClassUtils.getUserClass(type));
            for (Class<?> candidate : candidates) {
                if (candidate.getSimpleName().equals("ExpirePermissionAdjustments") && candidate.getPackageName().startsWith("com.forcitis.one.platform.identity")) {
                    try {
                        candidate.getMethod("execute").invoke(applicationContext.getBean(name));
                        return;
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        if (failure.getCause() instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        throw new IllegalStateException(failure.getCause());
                    } catch (ReflectiveOperationException missing) {
                        throw new AssertionError("ExpirePermissionAdjustments has no public execute() without arguments", missing);
                    }
                }
            }
        }
        throw new AssertionError("ADR-0044 draft: no bean of a type named ExpirePermissionAdjustments in platform.identity (the uphold expiry entry)");
    }

    protected MockHttpServletResponse uphold(String session, String adjustmentId, String body) throws Exception {
        String etag = etagOf(session, adjustmentId);
        var request = post(PERMISSION_ADJUSTMENTS + "/" + adjustmentId + ":uphold").with(csrf()).with(as(session))
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(etag == null ? request : request.header("If-Match", etag)).andReturn().getResponse();
    }

    /** The version as the caller reads it, else as the organisation data administrator does (a refused caller still sends a valid If-Match). */
    private String etagOf(String session, String adjustmentId) throws Exception {
        String own = readAdjustment(session, adjustmentId).getHeader("ETag");
        if (own != null || !people.containsKey(ORG_DATA_ADMINISTRATOR_KEY)) {
            return own;
        }
        return readAdjustment(session(ORG_DATA_ADMINISTRATOR_KEY), adjustmentId).getHeader("ETag");
    }

    /** The person every F-1 world grants org_data_admin to. */
    protected static final String ORG_DATA_ADMINISTRATOR_KEY = "oda";

    /**
     * Denials recorded for a subject: ADR-0026 section 6 item 5 names platform.identity.access.denied; the module-specific
     * organization.workforce.*.denied of the earlier fix count as well (one-coordinator 2026-10-15: a 404 scope refusal is still audited).
     */
    protected List<Map<String, Object>> denialsAbout(String subjectId) throws Exception {
        List<Map<String, Object>> found = new ArrayList<>();
        for (String prefix : List.of("platform.identity.access.denied", "organization.workforce")) {
            MockHttpServletResponse entries = mvc.perform(get(AUDIT_ENTRIES).param("actionPrefix", prefix).param("limit", "200").with(AUDIT_READER))
                    .andReturn().getResponse();
            require(entries, 200, "the audit query");
            items(entries).stream().filter(entry -> String.valueOf(entry.get("action")).endsWith("denied")
                    && String.valueOf(entry.get("subjectId")).contains(subjectId)).forEach(found::add);
        }
        return found;
    }

    // ---- reading -------------------------------------------------------------------------------------------------------------

    /** The list items, whether the list answers {items, nextCursor} (NAMING_STANDARD A-3) or a bare array (the shape before K-3). */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> items(MockHttpServletResponse answer) throws Exception {
        Object root = JsonPath.read(answer.getContentAsString(), "$");
        if (root instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return JsonPath.read(answer.getContentAsString(), "$.items");
    }

    protected static List<String> field(List<Map<String, Object>> items, String name) {
        return items.stream().map(item -> String.valueOf(item.get(name))).toList();
    }

    /** Org unit ids by code, read by the organisation data administrator, whose role has the whole organisation in scope. */
    protected String orgUnitId(String organisationDataAdministrator, String code) throws Exception {
        String known = orgUnitIds.get(code);
        if (known != null) {
            return known;
        }
        MockHttpServletResponse listed = mvc.perform(get(ORG_UNITS).with(as(organisationDataAdministrator))).andReturn().getResponse();
        require(listed, 200, "fixture: listing org units as the organisation data administrator");
        for (Map<String, Object> unit : items(listed)) {
            orgUnitIds.put(String.valueOf(unit.get("code")), String.valueOf(unit.get("orgUnitId")));
        }
        String id = orgUnitIds.get(code);
        if (id == null) {
            throw new AssertionError("fixture: org unit " + code + " is not in the seeded organisation");
        }
        return id;
    }

    /** An id of the same form as the sample that names nothing: a fresh UUID, or for numeric ids the largest number. */
    protected static String absentIdLike(String sample) {
        try {
            UUID.fromString(sample);
            return UUID.randomUUID().toString();
        } catch (IllegalArgumentException notAUuid) {
            return String.valueOf(Long.MAX_VALUE);
        }
    }

    protected static String errorCode(MockHttpServletResponse answer) throws Exception {
        Object code = JsonPath.read(answer.getContentAsString(), "$.errorCode");
        return String.valueOf(code);
    }

    protected static void require(MockHttpServletResponse answer, int status, String what) throws Exception {
        if (answer.getStatus() != status) {
            throw new AssertionError(what + " answered " + answer.getStatus() + " (expected " + status + "): " + answer.getContentAsString());
        }
    }

    protected static void eventually(ThrowingAssertion assertion) {
        await().atMost(ASYNC_LIMIT).untilAsserted(assertion::check);
    }

    @FunctionalInterface
    protected interface ThrowingAssertion {
        void check() throws Exception;
    }

    protected static String cookie(MockHttpServletResponse answer, String name) {
        for (String header : answer.getHeaders("Set-Cookie")) {
            if (header.startsWith(name + "=")) {
                int end = header.indexOf(';');
                return header.substring(name.length() + 1, end < 0 ? header.length() : end);
            }
        }
        Cookie set = answer.getCookie(name);
        return set == null ? null : set.getValue();
    }

    private static <T> T known(Map<String, T> world, String key) {
        T found = world.get(key);
        assertThat(found).as("fixture: " + key + " is declared before use").isNotNull();
        return found;
    }
}
