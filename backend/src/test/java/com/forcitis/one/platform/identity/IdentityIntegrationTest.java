package com.forcitis.one.platform.identity;

import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.organization.workforce.api.EmployeeExistence;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.application.AccessCache;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import com.forcitis.one.platform.integration.api.SsoTicketVerifier;
import com.forcitis.one.platform.integration.api.TicketSubjects;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.api.WecomGateway;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** WeCom, the portal ticket check, the organisation directory and the audit trail belong to other modules and are doubles. */
@SpringBootTest(properties = {
        "flowable.async-executor-activate=false",
        "forcitis.platform.identity.sso.enabled=true",
        "forcitis.platform.identity.login-throttle.limits.login-states=100000",
        "forcitis.platform.identity.login-throttle.limits.wecom-login=100000",
        "forcitis.platform.identity.login-throttle.limits.sso-login=100000",
        "forcitis.platform.identity.login-throttle.limits.ticket-login=100000",
        "forcitis.platform.identity.login-throttle.limits.emergency-login=100000",
        "forcitis.platform.identity.sso.legacy-login-url=https://legacy.example.invalid/login",
        "FORCITIS_TICKET_KEY_SHORT=too-short"})
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(IdentityIntegrationTest.ClockConfiguration.class)
public abstract class IdentityIntegrationTest {

    @Autowired
    ApplicationEvents recordedEvents;

    /** Throw-away key per test run (no key literal in the repo). */
    static final String TOTP_KEY = Base64.getEncoder().encodeToString(randomBytes(32));
    static final String KM_KEY = HexFormat.of().formatHex(randomBytes(32));

    static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    static final PostgreSQLContainer POSTGRES = TestPostgres.container()
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("forcitis.platform.identity.emergency.totp-encryption-key", () -> TOTP_KEY);
        registry.add("FORCITIS_TICKET_KEY_KM", () -> KM_KEY);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        DomainEventRecorder domainEventRecorder() {
            return new DomainEventRecorder();
        }
    }

    /** Sees every domain event on the thread that publishes it, including the asynchronous listeners' own threads. */
    static class DomainEventRecorder {
        private final List<DomainEvent> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(DomainEvent event) {
            seen.add(event);
        }

        <T extends DomainEvent> List<T> of(Class<T> type) {
            return seen.stream().filter(type::isInstance).map(type::cast).toList();
        }

        void clear() {
            seen.clear();
        }
    }

    @Autowired
    protected DomainEventRecorder domainEvents;

    @MockitoBean
    protected OrgDirectory orgDirectory;

    /** Exists where the double directory knows the person: the tests describe people through the directory. */
    @MockitoBean
    protected EmployeeExistence employeeExistence;

    @MockitoBean
    protected WecomGateway wecomGateway;

    @MockitoBean
    protected AuditLog auditLog;

    @MockitoBean
    protected SsoTicketVerifier ssoVerifier;

    @MockitoBean
    protected TicketSubjects ticketSubjects;


    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected AccessCache accessCache;

    /** Not spring-security-test's csrf(): that swaps the CSRF repository of the whole shared context. */
    protected static RequestPostProcessor xsrf() {
        return request -> {
            request.setCookies(Stream.concat(
                    request.getCookies() == null ? Stream.<Cookie>empty() : Arrays.stream(request.getCookies()),
                    Stream.of(new Cookie("XSRF-TOKEN", "test-xsrf-token"))).toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", "test-xsrf-token");
            return request;
        };
    }

    @BeforeEach
    void cleanDatabase() {
        clock.reset();
        domainEvents.clear();
        accessCache.evictAll();
        Mockito.reset(ssoVerifier);
        when(employeeExistence.exists(ArgumentMatchers.any())).thenAnswer(call -> orgDirectory.findEmployee(call.getArgument(0)).isPresent());
        when(orgDirectory.employmentOf(ArgumentMatchers.any()))
                .thenReturn(EmploymentVerdict.SNAPSHOT_UNAVAILABLE);
        for (String attestor : List.of("01900000-0000-7000-8000-00000000a001", "01900000-0000-7000-8000-00000000a002")) {
            EmployeeId attestorId = EmployeeId.parse(attestor);
            when(orgDirectory.findEmployee(attestorId)).thenReturn(Optional.of(new EmployeeProfile(attestorId, "Synthetic attestor", TestOrgIds.orgUnit(10),
                    null, EmploymentStatus.EMPLOYED, false)));
        }
        jdbc.sql("delete from platform_identity.sessions").update();
        jdbc.sql("delete from platform_identity.login_states").update();
        jdbc.sql("delete from platform_identity.sso_ticket_uses").update();
        jdbc.sql("delete from platform_identity.ticket_issuers").update();
        jdbc.sql("delete from platform_identity.emergency_admins").update();
        TestRoles.deleteAllGrants(jdbc);
        TestRoles.deleteCustomRoles(jdbc);
        jdbc.sql("delete from platform_identity.user_accounts").update();
    }

    protected static EmployeeProfile profile(long id, EmploymentStatus status) {
        return new EmployeeProfile(TestAccounts.employee(id), "Employee " + id, TestOrgIds.orgUnit(10), new WecomUserId("wx" + id), status, false);
    }

    protected EmployeeProfile knownEmployee(long id, EmploymentStatus status) {
        EmployeeProfile profile = profile(id, status);
        when(orgDirectory.findEmployee(profile.employeeId())).thenReturn(Optional.of(profile));
        when(orgDirectory.findEmployeeByLogin(new LoginLookup.ByWecomUser(profile.wecomUserId()))).thenReturn(Optional.of(profile));
        when(orgDirectory.employmentOf(profile.employeeId())).thenReturn(status == EmploymentStatus.EMPLOYED
                ? EmploymentVerdict.EMPLOYED : EmploymentVerdict.SEPARATED);
        when(orgDirectory.findOrgUnit(TestOrgIds.orgUnit(10))).thenReturn(Optional.of(new OrgUnitProfile(TestOrgIds.orgUnit(10), "Delivery", null, true)));
        return profile;
    }

    protected void wecomAccepts(String code, WecomEntry entry, long id) {
        when(wecomGateway.resolveWecomUserId(code, entry)).thenReturn(Optional.of(new WecomUserId("wx" + id)));
    }

    protected void createAccount(long id, String... roleCodes) {
        jdbc.sql("insert into platform_identity.user_accounts (user_id, employee_id) values (:user, :employee) on conflict do nothing")
                .param("user", TestAccounts.account(id).value()).param("employee", TestAccounts.employee(id).value()).update();
        for (String code : roleCodes) {
            jdbc.sql("""
                            insert into platform_identity.account_role_grants (account_role_grant_id, user_id, role_id, effective_from_at, grant_channel_kind,
                                   grant_audit_entry_id, created_at, updated_at)
                            select uuidv7(), a.user_id, r.role_id, now(), 'OPERATIONS', uuidv7(), now(), now() from platform_identity.user_accounts a, platform_identity.roles r
                             where a.employee_id = :employee and lower(r.code) = lower(:code)
                            on conflict do nothing
                            """)
                    .param("employee", TestAccounts.employee(id).value()).param("code", code).update();
        }
    }

    /** The organisation source's person n is test employee n: what the confirmed (ORG_SOURCE, EMPLOYEE) reference of platform.integration answers. */
    protected void organisationSourceKnows(long person) {
        when(ticketSubjects.employeeOf(String.valueOf(person))).thenReturn(Optional.of(TestAccounts.employee(person)));
    }

    /** The account of employee id, whether the test opened it or a login did. */
    protected UUID accountOf(long id) {
        return jdbc.sql("select user_id from platform_identity.user_accounts where employee_id = :id").param("id", TestAccounts.employee(id).value()).query(UUID.class).single();
    }

    /** The account of employee id, or the id the test double would give it when it has none (a path to nobody). */
    protected UUID accountOrNone(long id) {
        return jdbc.sql("select user_id from platform_identity.user_accounts where employee_id = :id").param("id", TestAccounts.employee(id).value()).query(UUID.class)
                .optional().orElse(TestAccounts.account(id).value());
    }

    protected String accountVersion(long id) {
        return "\"" + jdbc.sql("select version from platform_identity.user_accounts where employee_id = :id").param("id", TestAccounts.employee(id).value()).query(Long.class).optional().orElse(0L) + "\"";
    }

    protected String roleVersion(UUID roleId) {
        return "\"" + jdbc.sql("select version from platform_identity.roles where role_id = :id").param("id", roleId).query(Long.class).single() + "\"";
    }

    protected long count(String table) {
        return jdbc.sql("select count(*) from platform_identity." + table).query(Long.class).single();
    }

    protected MvcResult postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    protected final Map<String, String> bindings = new ConcurrentHashMap<>();

    /** Asks for a login state as a browser would and keeps the binding cookie that came with it. */
    protected String newLoginState(String entry) throws Exception {
        MvcResult issued = postJson("/api/v1/platform/login-states", "{\"entry\":\"" + entry + "\"}");
        String state = body(issued).path("state").asString();
        bindings.put(state, cookie(issued, "loginBinding"));
        return state;
    }

    /** Posts the login with the binding cookie of the browser that obtained the state. */
    protected MvcResult postLogin(String url, String body, String state) throws Exception {
        return postLogin(url, body, state, "127.0.0.1");
    }

    protected MvcResult postLogin(String url, String body, String state, String remoteAddress) throws Exception {
        var request = post(url).with(xsrf()).with(r -> {
            r.setRemoteAddr(remoteAddress);
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content(body);
        String binding = state == null ? null : bindings.get(state);
        if (binding != null) {
            request = request.cookie(new Cookie("loginBinding", binding));
        }
        return mvc.perform(request).andReturn();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static String cookie(MvcResult result, String name) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith(name + "=")) {
                return header.substring(name.length() + 1, header.indexOf(';') < 0 ? header.length() : header.indexOf(';'));
            }
        }
        return null;
    }

    protected static String setCookieHeader(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(name + "=")).findFirst().orElse(null);
    }

    protected String loginWithWecom(long id) throws Exception {
        knownEmployee(id, EmploymentStatus.EMPLOYED);
        String code = "code-" + id;
        wecomAccepts(code, WecomEntry.WORKBENCH, id);
        String state = newLoginState("WORKBENCH");
        MvcResult result = postLogin("/api/v1/platform/sessions:wecom-login",
                "{\"code\":\"" + code + "\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}", state);
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("login failed: " + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
        }
        return cookie(result, "SESSION");
    }

    protected static final String ENTER_ADMINISTRATOR_MODE = "/api/v1/platform/sessions:enter-administrator-mode";

    /** The person's session asks for the administrator mode, proving themselves with a fresh WeCom code of person personProvedByTheCode. */
    protected MvcResult enterAdministratorMode(String session, long personProvedByTheCode) throws Exception {
        knownEmployee(personProvedByTheCode, EmploymentStatus.EMPLOYED);
        String code = "reverify-" + personProvedByTheCode + "-" + System.nanoTime();
        wecomAccepts(code, WecomEntry.WORKBENCH, personProvedByTheCode);
        String state = newLoginState("WORKBENCH");
        return mvc.perform(post(ENTER_ADMINISTRATOR_MODE).with(xsrf()).cookie(new Cookie("SESSION", session))
                .cookie(new Cookie("loginBinding", bindings.get(state))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}")).andReturn();
    }

    /** A session in the administrator mode for a super_admin holder who is employed. */
    protected String administratorSession(long id) throws Exception {
        createAccount(id, "super_admin", "employee");
        MvcResult entered = enterAdministratorMode(loginWithWecom(id), id);
        if (entered.getResponse().getStatus() != 200) {
            throw new AssertionError("entering the administrator mode failed: " + entered.getResponse().getContentAsString());
        }
        return cookie(entered, "SESSION");
    }

    @Autowired
    protected EmergencyAdminRepository emergencyAdmins;

    @Autowired
    protected PasswordHasher hasher;

    @Autowired
    protected TotpSecretVault vault;

    /** A session started by the emergency login of a new emergency account bound to super_admin holder userId. */
    protected String emergencySession(long userId) throws Exception {
        createAccount(userId, "super_admin");
        byte[] secret = Totp.newSecret();
        String password = "Aa1!" + UUID.randomUUID();
        emergencyAdmins.insertIfAbsent(EmergencyAdmin.register(new UserId(accountOf(userId)), "breakglass-" + userId,
                hasher.hash(password), vault.seal(secret)));
        MvcResult login = mvc.perform(post("/api/v1/platform/sessions:emergency-login").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"loginName\":\"breakglass-" + userId + "\",\"password\":\"" + password + "\",\"totpCode\":\""
                        + Totp.code(secret, Totp.stepAt(clock.instant())) + "\"}")).andReturn();
        if (login.getResponse().getStatus() != 200) {
            throw new AssertionError("emergency login failed: " + login.getResponse().getContentAsString());
        }
        return cookie(login, "SESSION");
    }

    protected List<AuditEntry> recorded() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        Mockito.verify(auditLog, Mockito.atLeast(0)).record(captor.capture());
        return captor.getAllValues();
    }

    protected List<AuditEntry> recordedIndependently() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        Mockito.verify(auditLog, Mockito.atLeast(0)).recordIndependently(captor.capture());
        return captor.getAllValues();
    }

    protected static String responseText(MvcResult result) throws Exception {
        MockHttpServletResponse response = result.getResponse();
        return response.getContentAsString();
    }
}
