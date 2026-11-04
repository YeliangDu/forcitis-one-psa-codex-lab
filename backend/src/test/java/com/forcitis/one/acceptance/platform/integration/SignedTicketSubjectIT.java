package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.jayway.jsonpath.JsonPath;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P3b, ADR-0033 section 3 item 5 (card section 3.2 item 4): the subject of a signed ticket is the organisation source's person id. It is
 * resolved to the employee through the confirmed (ORG_SOURCE, EMPLOYEE) source reference, looked up and never allocated; without a
 * reference the login is refused and audited, and no reference is made. Until P3b the subject is taken as the employee id itself.
 * The ticket is signed here with the wire form of ADR-0009 section 3 (base64url(kid|issuer|subject|nonce|issuedAt|expiresAt) "." base64url(HMAC-SHA256));
 * the issuer key is generated per run and handed to the application as the environment property its key reference names.
 */
class SignedTicketSubjectIT extends AccountsAcceptanceSupport {

    static final String TICKET_LOGIN = "/api/v1/platform/sessions:ticket-login";
    static final String ORG_SOURCE = "ORG_SOURCE";
    static final String EMPLOYEE = "EMPLOYEE";
    static final String ISSUER = "km";
    static final String KID = "acceptance-p3b-key";
    /** The environment property the issuer's key reference names (env:FORCITIS_TICKET_KEY_*); its value is made per run. */
    static final String KEY_PROPERTY = String.join("_", "FORCITIS", "TICKET", "KEY", "ACCEPTANCE", "P3B");
    static final String KEY = randomKey();

    static final TestDatabase postgres = TestPostgres.database(SignedTicketSubjectIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @DynamicPropertySource
    static void issuerKey(DynamicPropertyRegistry registry) {
        registry.add(KEY_PROPERTY, () -> KEY);
    }

    @Autowired
    ApplicationContext context;

    private static String randomKey() {
        byte[] key = new byte[36];
        new SecureRandom().nextBytes(key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    @BeforeEach
    void anEnabledIssuer() throws Exception {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement insert = identity.prepareStatement("""
                insert into ticket_issuers (ticket_issuer_id, issuer_code, signing_key_code, key_reference, is_enabled, allowed_role_codes)
                values (?, ?, ?, ?, true, '{employee}') on conflict do nothing""")) {
            insert.setObject(1, Uuids.timeOrdered(NOW));
            insert.setString(2, ISSUER);
            insert.setString(3, KID);
            insert.setString(4, "env:" + KEY_PROPERTY);
            insert.executeUpdate();
        }
    }

    private UUID allocate(long sourceId) {
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            return SourceReferencesProbe.of(context).allocate(ORG_SOURCE, EMPLOYEE, sourceId);
        }
    }

    private int references(long sourceId) throws Exception {
        try (Connection integration = schemaConnection("platform_integration"); PreparedStatement count = integration.prepareStatement(
                "select count(*) from source_references where source_system = ? and source_object_kind = ? and source_id = ?")) {
            count.setString(1, ORG_SOURCE);
            count.setString(2, EMPLOYEE);
            count.setLong(3, sourceId);
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static String ticket(long subject, Instant now) throws Exception {
        return ticket(Long.toString(subject), now);
    }

    /** A ticket whose subject field is the given text, also one that no 64-bit number can hold. */
    static String ticket(String subject, Instant now) throws Exception {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        byte[] payload = (KID + "|" + ISSUER + "|" + subject + "|" + nonce + "|" + now.minusSeconds(1).getEpochSecond() + "|"
                + now.plusSeconds(30).getEpochSecond()).getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payload) + "." + encoder.encodeToString(mac.doFinal(payload));
    }

    private MockHttpServletResponse redeem(String ticket) throws Exception {
        return mvc.perform(post(TICKET_LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ticket\":\"" + ticket + "\"}"))
                .andReturn().getResponse();
    }

    // RULE-472
    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 5 条：签名票据的 subject（组织源人员 ID，含负数）经来源对照解析为员工，登录得到该员工的账号；只查不分配，对照仍只有一行")
    void theSubjectIsResolvedThroughTheSourceReference() throws Exception {
        long subject = -(Long.MAX_VALUE / 1_000) + 17;
        UUID person = adoptId(allocate(subject));
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        employee(person, unit, null);
        SignedIn account = signIn(person);
        grantRole(account.userId(), "employee");

        MockHttpServletResponse login = redeem(ticket(subject, clock.instant()));

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(login.getContentAsString(), "$.userId")).isEqualTo(account.userId().toString());
        assertThat(references(subject)).as("the login looked the reference up, it made none").isEqualTo(1);
    }

    // RULE-472
    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 5 条：签名票据的 subject 没有来源对照即拒绝登录（401）并记审计，不建会话，也不为它分配对照")
    void aSubjectWithoutReferenceIsRefusedAndAudited() throws Exception {
        long subject = -(Long.MAX_VALUE / 1_000) + 29;

        MockHttpServletResponse login = redeem(ticket(subject, clock.instant()));

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(401);
        assertThat(login.getHeaders("Set-Cookie")).noneMatch(header -> header.startsWith("SESSION="));
        assertThat(auditedActions("platform.identity.login.failed")).isNotEmpty();
        assertThat(references(subject)).as("no reference is allocated for a ticket subject").isZero();
    }

    // RULE-472
    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 5 条：票据 subject 不再被当作员工 ID——数值恰等于某员工在旧形态下编号、却没有对照的 subject 被拒")
    void theSubjectIsNotTheEmployeeId() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        SignedIn account = signIn(person);
        grantRole(account.userId(), "employee");
        long subject = serialOf(person);

        MockHttpServletResponse login = redeem(ticket(subject, clock.instant()));

        assertThat(login.getStatus()).as("subject %s names no source reference: %s", subject, login.getContentAsString()).isEqualTo(401);
        assertThat(references(subject)).isZero();
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-6：票据 subject 接受完整 64 位有符号范围——subject 为 Long.MIN_VALUE（19 位负数）且有来源对照时登录成功")
    void theLowestSixtyFourBitSubjectSignsIn() throws Exception {
        UUID person = adoptId(allocate(Long.MIN_VALUE));
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        employee(person, unit, null);
        SignedIn account = signIn(person);
        grantRole(account.userId(), "employee");

        MockHttpServletResponse login = redeem(ticket(Long.MIN_VALUE, clock.instant()));

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(login.getContentAsString(), "$.userId")).isEqualTo(account.userId().toString());
        assertThat(references(Long.MIN_VALUE)).isEqualTo(1);
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-6：票据 subject 超出 64 位有符号范围（Long.MIN_VALUE - 1、Long.MAX_VALUE + 1）答 401，不建会话")
    void aSubjectBeyondSixtyFourBitsIsRefused() throws Exception {
        for (BigInteger beyond : new BigInteger[] {BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)}) {
            MockHttpServletResponse login = redeem(ticket(beyond.toString(), clock.instant()));

            assertThat(login.getStatus()).as("subject %s: %s", beyond, login.getContentAsString()).isEqualTo(401);
            assertThat(login.getHeaders("Set-Cookie")).noneMatch(header -> header.startsWith("SESSION="));
        }
    }
}
