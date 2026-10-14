package com.forcitis.one.platform.identity;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.application.AccessCache;
import com.forcitis.one.platform.identity.application.LoginNameDirectory;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared set-up of the identity integration tests: one PostgreSQL 18 container and one WireMock server (standing in for the
 * Seeyon OA) for the whole test run, so the Spring context is built once. WeCom, the organisation directory and the audit trail
 * belong to other modules and are doubles; nothing real is contacted.
 */
@SpringBootTest(properties = {
        "flowable.async-executor-activate=false",
        "forcitis.platform.identity.seeyon.enabled=true",
        "forcitis.platform.identity.seeyon.timeout=1s",
        "forcitis.platform.identity.seeyon.legacy-login-url=https://legacy.example.invalid/login",
        "FORCITIS_TICKET_KEY_SHORT=too-short"})
@AutoConfigureMockMvc
@Import(IdentityIntegrationTest.ClockConfiguration.class)
abstract class IdentityIntegrationTest {

    /** Base64 of 32 random bytes, generated per test run: a throw-away key for the TOTP vault (no key literal in the repo). */
    static final String TOTP_KEY = Base64.getEncoder().encodeToString(randomBytes(32));
    /** HMAC key of the ticket issuer "km", generated per test run (64 hex characters). */
    static final String KM_KEY = HexFormat.of().formatHex(randomBytes(32));

    static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");
    static final WireMockServer OA = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        POSTGRES.start();
        OA.start();
        Runtime.getRuntime().addShutdownHook(new Thread(OA::stop));
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("forcitis.platform.identity.seeyon.base-url", OA::baseUrl);
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
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @MockitoBean
    LoginNameDirectory loginNames;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    MutableClock clock;

    @Autowired
    AccessCache accessCache;

    /**
     * The real CSRF handshake of the single-page app: the XSRF-TOKEN cookie written back in the X-XSRF-TOKEN header. (Not
     * spring-security-test's csrf(): that swaps the CSRF repository of the whole shared context for one that uses sessions.)
     */
    static RequestPostProcessor xsrf() {
        return request -> {
            request.setCookies(java.util.stream.Stream.concat(
                    request.getCookies() == null ? java.util.stream.Stream.<jakarta.servlet.http.Cookie>empty() : java.util.Arrays.stream(request.getCookies()),
                    java.util.stream.Stream.of(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "test-xsrf-token"))).toArray(jakarta.servlet.http.Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", "test-xsrf-token");
            return request;
        };
    }

    @BeforeEach
    void cleanDatabase() {
        clock.reset();
        accessCache.evictAll();
        OA.resetAll();
        jdbc.sql("delete from platform_identity.sessions").update();
        jdbc.sql("delete from platform_identity.login_states").update();
        jdbc.sql("delete from platform_identity.sso_ticket_uses").update();
        jdbc.sql("delete from platform_identity.ticket_issuers").update();
        jdbc.sql("delete from platform_identity.emergency_admins").update();
        jdbc.sql("delete from platform_identity.account_roles").update();
        jdbc.sql("delete from platform_identity.user_accounts").update();
        jdbc.sql("delete from platform_identity.roles where kind = 'CUSTOM'").update();
    }

    // ---------- organisation doubles ----------

    static EmployeeProfile profile(long id, EmploymentStatus status) {
        return new EmployeeProfile(new UserId(id), "Employee " + id, new OrgUnitId(10), new WecomUserId("wx" + id), status, false);
    }

    /** The directory knows this person, by id and by WeCom user id wx{id}; their org unit exists. */
    EmployeeProfile knownEmployee(long id, EmploymentStatus status) {
        EmployeeProfile profile = profile(id, status);
        when(orgDirectory.findEmployee(profile.userId())).thenReturn(Optional.of(profile));
        when(orgDirectory.findEmployeeByWecomUserId(profile.wecomUserId())).thenReturn(Optional.of(profile));
        when(orgDirectory.findOrgUnit(new OrgUnitId(10))).thenReturn(Optional.of(new OrgUnitProfile(new OrgUnitId(10), "Delivery", null, true)));
        return profile;
    }

    /** A WeCom code that WeCom accepts for this person. */
    void wecomAccepts(String code, WecomEntry entry, long id) {
        when(wecomGateway.resolveWecomUserId(code, entry)).thenReturn(Optional.of(new WecomUserId("wx" + id)));
    }

    // ---------- database helpers ----------

    void createAccount(long id, String... roleCodes) {
        jdbc.sql("insert into platform_identity.user_accounts (user_id) values (:id) on conflict do nothing").param("id", id).update();
        for (String code : roleCodes) {
            jdbc.sql("""
                            insert into platform_identity.account_roles (user_id, role_id)
                            select :id, role_id from platform_identity.roles where lower(code) = lower(:code)
                            """)
                    .param("id", id).param("code", code).update();
        }
    }

    long count(String table) {
        return jdbc.sql("select count(*) from platform_identity." + table).query(Long.class).single();
    }

    // ---------- HTTP helpers ----------

    MvcResult postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** The value of a cookie set by the response (SESSION, XSRF-TOKEN); null when it was not set. */
    static String cookie(MvcResult result, String name) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith(name + "=")) {
                return header.substring(name.length() + 1, header.indexOf(';') < 0 ? header.length() : header.indexOf(';'));
            }
        }
        return null;
    }

    static String setCookieHeader(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(name + "=")).findFirst().orElse(null);
    }

    /** Full WeCom workbench login for an employed, known person; returns the SESSION cookie value. */
    String loginWithWecom(long id) throws Exception {
        knownEmployee(id, EmploymentStatus.EMPLOYED);
        String code = "code-" + id;
        wecomAccepts(code, WecomEntry.WORKBENCH, id);
        String state = body(postJson("/api/v1/platform/login-states", "{\"entry\":\"WORKBENCH\"}")).path("state").asString();
        MvcResult result = postJson("/api/v1/platform/sessions:wecom-login",
                "{\"code\":\"" + code + "\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}");
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("login failed: " + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
        }
        return cookie(result, "SESSION");
    }

    // ---------- audit helpers ----------

    List<AuditEntry> recorded() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        Mockito.verify(auditLog, Mockito.atLeast(0)).record(captor.capture());
        return captor.getAllValues();
    }

    List<AuditEntry> recordedIndependently() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        Mockito.verify(auditLog, Mockito.atLeast(0)).recordIndependently(captor.capture());
        return captor.getAllValues();
    }

    static String responseText(MvcResult result) throws Exception {
        MockHttpServletResponse response = result.getResponse();
        return response.getContentAsString();
    }
}
