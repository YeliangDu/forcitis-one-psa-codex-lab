package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.identity.domain.SignedTicket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;

class SignedTicketLoginIntegrationTest extends IdentityIntegrationTest {

    static final String LOGIN = "/api/v1/platform/sessions:ticket-login";
    static final byte[] KM_SECRET = KM_KEY.getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void employeeWithKmIssuer() {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        createAccount(1001, "employee");
        insertIssuer("km", "km-key-1", "env:FORCITIS_TICKET_KEY_KM", true, "employee");
        organisationSourceKnows(1001);
        organisationSourceKnows(1002);
    }

    private void insertIssuer(String code, String kid, String keyReference, boolean enabled, String... roles) {
        jdbc.sql("""
                        insert into platform_identity.ticket_issuers (ticket_issuer_id, issuer_code, signing_key_code, key_reference, is_enabled, allowed_role_codes)
                        values (uuidv7(), :code, :kid, :ref, :enabled, string_to_array(:roles, ','))
                        """)
                .param("code", code).param("kid", kid).param("ref", keyReference).param("enabled", enabled)
                .param("roles", String.join(",", roles)).update();
    }

    private String ticket(long subject) {
        return ticket("km-key-1", "km", subject, UUID.randomUUID().toString().replace("-", ""), clock.instant().minusSeconds(1),
                clock.instant().plusSeconds(30), KM_SECRET);
    }

    private String ticket(String kid, String issuer, long subject, String nonce, Instant issuedAt, Instant expiresAt, byte[] key) {
        return SignedTicket.sign(key, kid, issuer, subject, nonce, issuedAt, expiresAt);
    }

    private MvcResult redeem(String ticket) throws Exception {
        return postJson(LOGIN, "{\"ticket\":\"" + ticket + "\"}");
    }

    private String errorCode(MvcResult result) throws Exception {
        return body(result).path("errorCode").asString();
    }

    @Test
    @DisplayName("C1a-03 a valid ticket of an enabled issuer starts a session for the person it names")
    void validTicket() throws Exception {
        MvcResult result = redeem(ticket(1001));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("userId").asString()).isEqualTo(accountOf(1001).toString());
        assertThat(cookie(result, "SESSION")).isNotBlank();
        assertThat(recorded()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("platform.identity.login.succeeded");
            assertThat(entry.detail()).contains("method=SIGNED_TICKET");
        });
    }

    @Test
    @DisplayName("C1a-03 every issuer starts switched off: a ticket of a disabled issuer is refused")
    void issuerIsOffByDefault() throws Exception {
        jdbc.sql("update platform_identity.ticket_issuers set is_enabled = false").update();
        assertThat(redeem(ticket(1001)).getResponse().getStatus()).isEqualTo(401);

        jdbc.sql("delete from platform_identity.ticket_issuers").update();
        jdbc.sql("insert into platform_identity.ticket_issuers (ticket_issuer_id, issuer_code, signing_key_code, key_reference) values (uuidv7(), 'bi', 'bi-key-1', 'env:FORCITIS_TICKET_KEY_KM')").update();
        assertThat(jdbc.sql("select is_enabled from platform_identity.ticket_issuers").query(Boolean.class).single())
                .as("the column default is off").isFalse();
    }

    @Test
    @DisplayName("I-9 a nonce can be used once: the second presentation of the same ticket is refused")
    void nonceIsOneTime() throws Exception {
        String ticket = ticket(1001);

        assertThat(redeem(ticket).getResponse().getStatus()).isEqualTo(200);
        MvcResult replay = redeem(ticket);

        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(replay)).isEqualTo("PLATFORM_IDENTITY_TICKET_REPLAYED");
        assertThat(jdbc.sql("select ticket_source from platform_identity.sso_ticket_uses").query(String.class).single()).isEqualTo("km");
    }

    @Test
    @DisplayName("C1a-03 a ticket lives at most 60 seconds: expired, from the future and too long-lived ones are refused")
    void timeWindow() throws Exception {
        Instant now = clock.instant();
        String expired = ticket("km-key-1", "km", 1001, "nonce-expired-0000000", now.minusSeconds(120), now.minusSeconds(60), KM_SECRET);
        String future = ticket("km-key-1", "km", 1001, "nonce-future-00000000", now.plusSeconds(600), now.plusSeconds(630), KM_SECRET);
        String longLived = ticket("km-key-1", "km", 1001, "nonce-long-000000000", now.minusSeconds(1), now.plusSeconds(3600), KM_SECRET);

        assertThat(redeem(expired).getResponse().getStatus()).isEqualTo(401);
        assertThat(redeem(future).getResponse().getStatus()).isEqualTo(401);
        assertThat(redeem(longLived).getResponse().getStatus()).isEqualTo(401);
        clock.advance(Duration.ofSeconds(61));
        assertThat(redeem(ticket("km-key-1", "km", 1001, "nonce-fresh-000000000", now, now.plusSeconds(60), KM_SECRET))
                .getResponse().getStatus()).as("issued 61 s ago with a 60 s life").isEqualTo(401);
    }

    @Test
    @DisplayName("C1a-03 forged tickets are refused: wrong key, changed subject, unknown key id, issuer that does not match the key id")
    void forgeries() throws Exception {
        Instant now = clock.instant();
        byte[] otherKey = "another-signing-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String wrongKey = ticket("km-key-1", "km", 1001, "nonce-wrong-key-0000", now, now.plusSeconds(30), otherKey);
        String unknownKid = ticket("km-key-9", "km", 1001, "nonce-unknown-kid-00", now, now.plusSeconds(30), KM_SECRET);
        String wrongIssuer = ticket("km-key-1", "bi", 1001, "nonce-wrong-issuer-0", now, now.plusSeconds(30), KM_SECRET);
        String genuine = ticket("km-key-1", "km", 1001, "nonce-tamper-0000000", now, now.plusSeconds(30), KM_SECRET);
        String tampered = tamperSubject(genuine);

        for (String bad : new String[] {wrongKey, unknownKid, wrongIssuer, tampered, "garbage", "a.b", "", genuine.substring(0, 20)}) {
            assertThat(redeem(bad).getResponse().getStatus()).as("ticket %s", bad).isEqualTo(401);
        }
        assertThat(count("sso_ticket_uses")).as("nothing was consumed by the forgeries").isZero();
    }

    private static String tamperSubject(String genuine) {
        String[] parts = genuine.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).replace("|1001|", "|1002|");
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[1];
    }

    @Test
    @DisplayName("C1a-03 the key is never in the database: a missing, short or foreign key reference makes every ticket invalid")
    void keyResolution() throws Exception {
        jdbc.sql("update platform_identity.ticket_issuers set key_reference = 'env:FORCITIS_TICKET_KEY_MISSING'").update();
        assertThat(redeem(ticket(1001)).getResponse().getStatus()).as("variable not set").isEqualTo(401);
        jdbc.sql("update platform_identity.ticket_issuers set key_reference = 'env:FORCITIS_TICKET_KEY_SHORT'").update();
        assertThat(redeem(ticket(1001)).getResponse().getStatus()).as("key shorter than 32 characters").isEqualTo(401);
        jdbc.sql("update platform_identity.ticket_issuers set key_reference = 'env:PATH'").update();
        assertThat(redeem(ticket(1001)).getResponse().getStatus()).as("only FORCITIS_TICKET_KEY_* may be referenced").isEqualTo(401);
        jdbc.sql("update platform_identity.ticket_issuers set key_reference = 'kms:alias/not-implemented'").update();
        assertThat(redeem(ticket(1001)).getResponse().getStatus()).isEqualTo(401);
    }

    // RULE-456
    @Test
    @DisplayName("C1a-03 a ticket can never bring in an administrator, whatever the issuer is configured for")
    void administratorsAreRefused() throws Exception {
        createAccount(1001, "super_admin");

        MvcResult result = redeem(ticket(1001));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(result)).isEqualTo("PLATFORM_IDENTITY_ROLE_NOT_ALLOWED");
        assertThat(cookie(result, "SESSION")).isNull();
        assertThatThrownBy(() -> insertIssuer("evil", "evil-key", "env:FORCITIS_TICKET_KEY_KM", true, "super_admin"))
                .as("the schema refuses an issuer scoped to super_admin").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("C1a-03 the issuer's user scope: only people holding one of its roles can come in; an empty scope lets nobody in")
    void userScope() throws Exception {
        createAccount(1002);
        knownEmployee(1002, EmploymentStatus.EMPLOYED);
        assertThat(redeem(ticket(1002)).getResponse().getStatus()).as("no role at all").isEqualTo(403);

        jdbc.sql("update platform_identity.ticket_issuers set allowed_role_codes = '{}'").update();
        assertThat(redeem(ticket(1001)).getResponse().getStatus()).as("empty scope").isEqualTo(403);
    }

    @Test
    @DisplayName("RULE-326/327 a ticket does not get past a disabled account or a separated employee")
    void sameGate() throws Exception {
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where employee_id = " + TestOrgIds.sqlEmployee(1001)).update();
        MvcResult disabled = redeem(ticket(1001));
        assertThat(disabled.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(disabled)).isEqualTo("PLATFORM_IDENTITY_ACCOUNT_DISABLED");

        jdbc.sql("update platform_identity.user_accounts set status = 'ENABLED' where employee_id = " + TestOrgIds.sqlEmployee(1001)).update();
        knownEmployee(1001, EmploymentStatus.SEPARATED);
        MvcResult separated = redeem(ticket(1001));
        assertThat(separated.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(separated)).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
    }

    @Test
    @DisplayName("RULE-318 there is no passwordless login: a person who is not an employee cannot be named in a ticket")
    void unknownSubject() throws Exception {
        assertThat(redeem(ticket(424242)).getResponse().getStatus()).isEqualTo(401);
    }

    // RULE-457
    @Test
    @DisplayName("ADR-0033 the subject is the organisation source's person id: an employee without that source reference cannot be named, and the refusal is audited")
    void subjectWithoutSourceReference() throws Exception {
        knownEmployee(1003, EmploymentStatus.EMPLOYED);
        createAccount(1003, "employee");

        MvcResult result = redeem(ticket(1003));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(result)).isEqualTo("PLATFORM_IDENTITY_TICKET_INVALID");
        assertThat(cookie(result, "SESSION")).isNull();
        assertThat(recordedIndependently()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("platform.identity.login.failed");
            assertThat(entry.detail()).contains("subject has no organisation source reference");
        });
    }

    // RULE-458
    @Test
    @DisplayName("C-M7 a ticket from another site starts a session that holds no permission until the person confirms the account is theirs")
    void ticketSessionNeedsConfirmation() throws Exception {
        MvcResult result = redeem(ticket(1001));
        String session = cookie(result, "SESSION");

        assertThat(body(result).path("identityConfirmationRequired").asBoolean()).isTrue();
        assertThat(mvc.perform(get("/api/v1/platform/approval-tasks").cookie(new jakarta.servlet.http.Cookie("SESSION", session))).andReturn()
                .getResponse().getStatus()).as("unconfirmed: no permission").isEqualTo(403);
        assertThat(body(mvc.perform(get("/api/v1/platform/sessions:current").cookie(new jakarta.servlet.http.Cookie("SESSION", session))).andReturn())
                .path("identityConfirmed").asBoolean()).isFalse();
        MvcResult unconfirmedProfile = mvc.perform(get("/api/v1/platform/sessions:current").cookie(new jakarta.servlet.http.Cookie("SESSION", session))).andReturn();
        assertThat(body(unconfirmedProfile).path("permissionCodes")).as("an unconfirmed session is shown no permission").isEmpty();
        assertThat(body(unconfirmedProfile).path("navigation")).as("and no menu").isEmpty();

        MvcResult confirmation = mvc.perform(post("/api/v1/platform/sessions:confirm-identity").with(xsrf()).cookie(new jakarta.servlet.http.Cookie("SESSION", session)))
                .andReturn();
        assertThat(confirmation.getResponse().getStatus()).isEqualTo(204);
        String confirmed = cookie(confirmation, "SESSION");
        assertThat(confirmed).as("the session id changes with the level of trust").isNotBlank().isNotEqualTo(session);
        assertThat(cookie(confirmation, "__Host-browserIdentifier")).as("the browser is given an identifier").isNotBlank();

        assertThat(mvc.perform(get("/api/v1/platform/approval-tasks").cookie(new jakarta.servlet.http.Cookie("SESSION", confirmed))).andReturn()
                .getResponse().getStatus()).as("confirmed: the employee role works").isNotEqualTo(403);
        assertThat(recorded()).extracting(com.forcitis.one.platform.audit.api.AuditEntry::action).contains("platform.identity.session.identity-confirmed");
    }
}
