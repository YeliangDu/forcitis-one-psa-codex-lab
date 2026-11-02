package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.fixture.doubles.TestAccounts;
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
import com.forcitis.one.kernel.event.TestEnvelopes;

/** C-M7: a ticket login skips the question "is this your account" only on a browser that holds a current identifier of that very account. */
class BrowserIdentifierIntegrationTest extends IdentityIntegrationTest {

    static final String IDENTIFIER = "__Host-browserIdentifier";
    static final String CONFIRM = "/api/v1/platform/sessions:confirm-identity";

    private final AtomicInteger tickets = new AtomicInteger();

    @BeforeEach
    void people() {
        for (long id : new long[] {1001, 1002}) {
            EmployeeProfile employee = knownEmployee(id, EmploymentStatus.EMPLOYED);
            when(orgDirectory.findEmployeeByLogin(new LoginLookup.ByLoginName("login" + id))).thenReturn(Optional.of(employee));
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
        return jdbc.sql("select count(*) from platform_identity.browser_identifiers where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :id)").param("id", TestAccounts.employee(id).value()).query(Long.class).single();
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
        var manager = user(TestAccounts.account(9).value().toString()).authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE);
        createAccount(9, "super_admin");

        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":disable").header("If-Match", accountVersion(1001)).with(xsrf()).with(manager)).andReturn().getResponse().getStatus())
                .isEqualTo(204);
        assertThat(storedIdentifiers(1001)).isZero();
        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":enable").header("If-Match", accountVersion(1001)).with(xsrf()).with(manager)).andReturn().getResponse().getStatus())
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

    @Test
    @DisplayName("DATA-REPAIR-identity ID-05 confirming on a browser retires the other accounts' rows of the old value and inserts new ones instead of rewriting the key")
    void dataRepairIdentityId05ReplaceRetiresAndInsertsForOtherAccounts() throws Exception {
        String shared = confirmedIdentifier(1001, null);
        String sharedHash = jdbc.sql("select identifier_hash from platform_identity.browser_identifiers").query(String.class).single();

        String ofSecond = confirmedIdentifier(1002, shared);

        assertThat(jdbc.sql("select count(*) from platform_identity.browser_identifiers where identifier_hash = :h and retired_at is not null")
                .param("h", sharedHash).query(Long.class).single()).as("the old value stays, retired, for the first account").isEqualTo(1);
        assertThat(storedIdentifiers(1001)).as("old row retired plus a new row").isEqualTo(2);
        assertThat(storedIdentifiers(1002)).isEqualTo(1);
        assertThat(body(login(1001, ofSecond)).path("identityConfirmationRequired").asBoolean()).as("trust moved along with the new value").isFalse();
        MvcResult replay = login(1001, shared);
        assertThat(body(replay).path("identityConfirmationRequired").asBoolean()).isTrue();
        assertThat(checkDetails()).last().asString().contains("reason=REPLAYED_IDENTIFIER");
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-05 the row created for another account keeps the old last login, so the trust period is not extended")
    void dataRepairIdentityId05MovedRowKeepsLastLoginSoTrustIsNotExtended() throws Exception {
        String shared = confirmedIdentifier(1001, null);
        clock.advance(Duration.ofDays(20));
        String ofSecond = confirmedIdentifier(1002, shared);
        clock.advance(Duration.ofDays(11));

        MvcResult result = login(1001, ofSecond);

        assertThat(body(result).path("identityConfirmationRequired").asBoolean()).as("31 days after the first confirmation, though only 11 after the move").isTrue();
        assertThat(checkDetails()).last().asString().contains("reason=EXPIRED_IDENTIFIER");
    }

    @org.springframework.beans.factory.annotation.Autowired
    com.forcitis.one.platform.identity.application.RevokeBrowserIdentifiers revoke;

    @Test
    @DisplayName("C-M7 the departure trigger invalidates the identifiers, and running it again changes nothing")
    void departureTriggerIsIdempotent() throws Exception {
        confirmedIdentifier(1001, null);
        assertThat(storedIdentifiers(1001)).isPositive();

        revoke.execute(new com.forcitis.one.kernel.UserId(accountOf(1001)));
        revoke.execute(new com.forcitis.one.kernel.UserId(accountOf(1001)));

        assertThat(storedIdentifiers(1001)).isZero();
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationEventPublisher events;

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.support.TransactionTemplate tx;

    @Test
    @DisplayName("C-M7 the account separation fact invalidates the identifiers of the person, and a second delivery changes nothing")
    void separationInvalidates() throws Exception {
        confirmedIdentifier(1001, null);
        confirmedIdentifier(1002, null);
        assertThat(storedIdentifiers(1001)).isPositive();
        com.forcitis.one.kernel.EmployeeId separated = TestAccounts.employee(1001);
        var snapshot = new com.forcitis.one.organization.workforce.events.EmployeeSeparated(
                TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", separated.value()),
                new com.forcitis.one.organization.workforce.api.PersonnelCommandId(java.util.UUID.randomUUID()), separated, java.time.LocalDate.of(2026, 10, 15));

        tx.executeWithoutResult(status -> events.publishEvent(snapshot));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(storedIdentifiers(1001)).isZero());
        tx.executeWithoutResult(status -> events.publishEvent(snapshot));

        assertThat(storedIdentifiers(1002)).as("nobody else is touched").isPositive();
    }

    @Test
    @DisplayName("B3-R1 a separated person whose account holds no role loses the browser identifiers too")
    void separationWithoutRolesInvalidates() throws Exception {
        confirmedIdentifier(1001, null);
        jdbc.sql("delete from platform_identity.account_roles where user_id = (select user_id from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(1001) + ")").update();
        assertThat(storedIdentifiers(1001)).isPositive();
        var snapshot = new com.forcitis.one.organization.workforce.events.EmployeeSeparated(
                TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", TestAccounts.employee(1001).value()),
                new com.forcitis.one.organization.workforce.api.PersonnelCommandId(java.util.UUID.randomUUID()), TestAccounts.employee(1001),
                java.time.LocalDate.of(2026, 10, 15));

        tx.executeWithoutResult(status -> events.publishEvent(snapshot));

        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(storedIdentifiers(1001)).isZero());
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
