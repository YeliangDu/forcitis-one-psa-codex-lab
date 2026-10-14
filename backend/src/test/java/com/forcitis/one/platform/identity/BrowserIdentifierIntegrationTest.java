package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.integration.api.SsoVerdict;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** C-M7: a ticket login skips the question "is this your account" only on a browser that holds a current identifier of that very account. */
class BrowserIdentifierIntegrationTest extends IdentityIntegrationTest {

    static final String IDENTIFIER = "__Host-browserIdentifier";
    static final String CONFIRM = "/api/v1/platform/sessions:confirm-identity";

    private final AtomicInteger tickets = new AtomicInteger();

    @BeforeEach
    void people() {
        for (long id : new long[] {1001, 1002}) {
            EmployeeProfile employee = knownEmployee(id, EmploymentStatus.EMPLOYED);
            when(orgDirectory.findEmployeeByLoginName("login" + id)).thenReturn(Optional.of(employee));
        }
    }

    private MvcResult login(long id, String identifier) throws Exception {
        String ticket = "T" + tickets.incrementAndGet();
        when(ssoVerifier.verify(ticket)).thenReturn(new SsoVerdict.LoginName("login" + id));
        var request = post(SsoLoginIntegrationTest.LOGIN).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{\"ticket\":\"" + ticket + "\"}");
        if (identifier != null) {
            request = request.cookie(new Cookie(IDENTIFIER, identifier));
        }
        return mvc.perform(request).andReturn();
    }

    private String confirmedIdentifier(long id, String presented) throws Exception {
        MvcResult first = login(id, presented);
        var request = post(CONFIRM).with(xsrf()).cookie(new Cookie("SESSION", cookie(first, "SESSION")));
        if (presented != null) {
            request = request.cookie(new Cookie(IDENTIFIER, presented));
        }
        return cookie(mvc.perform(request).andReturn(), IDENTIFIER);
    }

    private List<String> checkDetails() {
        return recorded().stream().filter(e -> e.action().equals("platform.identity.login.identity-check")).map(AuditEntry::detail).toList();
    }

    private long storedIdentifiers(long id) {
        return jdbc.sql("select count(*) from platform_identity.browser_identifiers where user_id = :id").param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("C-M7 no identifier: the login asks for confirmation, names the reason in the audit trail and sets no identifier yet")
    void noIdentifier() throws Exception {
        MvcResult result = login(1001, null);

        assertThat(body(result).path("identityConfirmationRequired").asBoolean()).isTrue();
        assertThat(cookie(result, IDENTIFIER)).isNull();
        assertThat(checkDetails()).singleElement().asString().contains("outcome=CONFIRMATION_REQUIRED").contains("reason=NO_IDENTIFIER");
    }

    @Test
    @DisplayName("C-M7 confirming issues an identifier (Secure, HttpOnly, SameSite=Lax); with it the next login needs no confirmation and gets a new value")
    void confirmedBrowserIsRecognisedAndTheValueRotates() throws Exception {
        String first = confirmedIdentifier(1001, null);
        assertThat(first).isNotBlank();
        assertThat(jdbc.sql("select identifier_hash from platform_identity.browser_identifiers").query(String.class).single())
                .as("only a hash is stored").isNotEqualTo(first).hasSize(64);

        MvcResult second = login(1001, first);

        assertThat(body(second).path("identityConfirmationRequired").asBoolean()).isFalse();
        String rotated = cookie(second, IDENTIFIER);
        assertThat(rotated).isNotBlank().isNotEqualTo(first);
        String header = setCookieHeader(second, IDENTIFIER);
        assertThat(header).contains("Secure").contains("HttpOnly").contains("SameSite=Lax").contains("Path=/").contains("Max-Age=2592000").doesNotContain("Domain");
        assertThat(mvc.perform(get("/api/v1/platform/approval-tasks").cookie(new Cookie("SESSION", cookie(second, "SESSION")))).andReturn()
                .getResponse().getStatus()).as("recognised: the session holds its permissions at once").isNotEqualTo(403);
        assertThat(checkDetails()).last().asString().contains("outcome=SKIPPED");
    }

    @Test
    @DisplayName("C-M7 the judgement is per account: an identifier issued for one person does not spare another person the question")
    void identifierOfAnotherAccount() throws Exception {
        String ofFirst = confirmedIdentifier(1001, null);

        MvcResult other = login(1002, ofFirst);

        assertThat(body(other).path("identityConfirmationRequired").asBoolean()).isTrue();
        assertThat(checkDetails()).last().asString().contains("reason=UNKNOWN_IDENTIFIER");
    }

    @Test
    @DisplayName("C-M7 an identifier not used for longer than its lifetime no longer counts")
    void expiredIdentifier() throws Exception {
        String issued = confirmedIdentifier(1001, null);
        clock.advance(Duration.ofDays(31));

        MvcResult result = login(1001, issued);

        assertThat(body(result).path("identityConfirmationRequired").asBoolean()).isTrue();
        assertThat(checkDetails()).last().asString().contains("reason=EXPIRED_IDENTIFIER");
    }

    @Test
    @DisplayName("C-M7 a rotated value shown again is a replay: every identifier of the account is invalidated, the event is audited as high risk, and the next login asks")
    void replayInvalidatesEverything() throws Exception {
        String old = confirmedIdentifier(1001, null);
        String current = cookie(login(1001, old), IDENTIFIER);
        assertThat(current).isNotEqualTo(old);

        MvcResult replay = login(1001, old);

        assertThat(body(replay).path("identityConfirmationRequired").asBoolean()).isTrue();
        assertThat(checkDetails()).last().asString().contains("reason=REPLAYED_IDENTIFIER");
        assertThat(recorded()).filteredOn(e -> e.action().equals("platform.identity.browser-identifier.replayed")).singleElement()
                .extracting(AuditEntry::detail).asString().contains("risk=HIGH");
        assertThat(storedIdentifiers(1001)).as("all of the account's identifiers are gone").isZero();
        MvcResult afterwards = login(1001, current);
        assertThat(body(afterwards).path("identityConfirmationRequired").asBoolean()).as("even the newest value no longer spares the question").isTrue();
    }

    @Test
    @DisplayName("C-M7 disabling the account invalidates its identifiers together with its sessions, so after enabling the person is asked again")
    void disablingInvalidates() throws Exception {
        String issued = confirmedIdentifier(1001, null);
        var manager = user("9").authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE);
        createAccount(9, "super_admin");

        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/1001:disable").header("If-Match", accountVersion(1001)).with(xsrf()).with(manager)).andReturn().getResponse().getStatus())
                .isEqualTo(204);
        assertThat(storedIdentifiers(1001)).isZero();
        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/1001:enable").header("If-Match", accountVersion(1001)).with(xsrf()).with(manager)).andReturn().getResponse().getStatus())
                .isEqualTo(204);

        MvcResult result = login(1001, issued);

        assertThat(body(result).path("identityConfirmationRequired").asBoolean()).isTrue();
        assertThat(checkDetails()).last().asString().contains("reason=UNKNOWN_IDENTIFIER");
    }

    @Test
    @DisplayName("C-M7 logging out keeps the identifier: the person is not asked again on the same browser")
    void logoutKeepsIdentifiers() throws Exception {
        String issued = confirmedIdentifier(1001, null);
        MvcResult session = login(1001, issued);
        String sessionCookie = cookie(session, "SESSION");

        assertThat(mvc.perform(post("/api/v1/platform/sessions:logout").with(xsrf()).cookie(new Cookie("SESSION", sessionCookie))).andReturn()
                .getResponse().getStatus()).isEqualTo(204);

        assertThat(storedIdentifiers(1001)).isEqualTo(2);
        assertThat(body(login(1001, cookie(session, IDENTIFIER))).path("identityConfirmationRequired").asBoolean()).isFalse();
    }

    @org.springframework.beans.factory.annotation.Autowired
    com.forcitis.one.platform.identity.application.RevokeBrowserIdentifiers revoke;

    @Test
    @DisplayName("C-M7 the departure trigger invalidates the identifiers, and running it again changes nothing")
    void departureTriggerIsIdempotent() throws Exception {
        confirmedIdentifier(1001, null);
        assertThat(storedIdentifiers(1001)).isPositive();

        revoke.execute(new com.forcitis.one.kernel.UserId(1001));
        revoke.execute(new com.forcitis.one.kernel.UserId(1001));

        assertThat(storedIdentifiers(1001)).isZero();
    }

    @org.springframework.beans.factory.annotation.Autowired
    com.forcitis.one.platform.identity.application.PurgeExpiredCredentials purge;

    @Test
    @DisplayName("C-M7 the clean-up job removes identifiers, current and rotated, once they are older than their lifetime, and only then")
    void cleanUpRemovesOldIdentifiers() throws Exception {
        String first = confirmedIdentifier(1001, null);
        login(1001, first);
        assertThat(storedIdentifiers(1001)).isEqualTo(2);

        purge.execute();
        assertThat(storedIdentifiers(1001)).as("still within the lifetime").isEqualTo(2);

        clock.advance(Duration.ofDays(31));
        purge.execute();
        assertThat(storedIdentifiers(1001)).isZero();
    }
}
