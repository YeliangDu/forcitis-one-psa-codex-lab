package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import com.forcitis.one.platform.identity.api.Permission;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class EmergencyLoginIntegrationTest extends IdentityIntegrationTest {

    static final String LOGIN = "/api/v1/platform/sessions:emergency-login";
    static final String PASSWORD = "Str0ng!Passphrase#42";

    @Autowired
    EmergencyAdminRepository admins;

    @Autowired
    MeterRegistry meters;

    @Autowired
    PasswordHasher hasher;

    @Autowired
    TotpSecretVault vault;

    byte[] secret;

    @BeforeEach
    void registerAdmin() {
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        createAccount(7, "super_admin");
        secret = Totp.newSecret();
        admins.insertIfAbsent(EmergencyAdmin.register(new UserId(7), "breakglass", hasher.hash(PASSWORD), vault.seal(secret)));
    }

    private String code() {
        return Totp.code(secret, Totp.stepAt(clock.instant()));
    }

    private MvcResult login(String name, String password, String code, String address) throws Exception {
        return mvc.perform(post(LOGIN).with(xsrf()).with(request -> {
            request.setRemoteAddr(address);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(
                "{\"loginName\":\"" + name + "\",\"password\":\"" + password + "\",\"totpCode\":\"" + code + "\"}")).andReturn();
    }

    private MvcResult login(String name, String password, String code) throws Exception {
        return login(name, password, code, "127.0.0.1");
    }

    @Test
    @DisplayName("C1a-04 password and a fresh one-time code from the internal network start a session")
    void login() throws Exception {
        MvcResult result = login("breakglass", PASSWORD, code());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(cookie(result, "SESSION")).isNotBlank();
        assertThat(recorded()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("platform.identity.login.succeeded");
            assertThat(entry.detail()).contains("method=EMERGENCY");
        });
    }

    @Test
    @DisplayName("C1a-04 the password is stored as Argon2id and the TOTP secret only encrypted")
    void secretsAreNotStoredInTheClear() {
        String hash = jdbc.sql("select password_hash from platform_identity.emergency_admins").query(String.class).single();
        String sealed = jdbc.sql("select totp_secret_encrypted from platform_identity.emergency_admins").query(String.class).single();

        assertThat(hash).startsWith("$argon2id$").doesNotContain(PASSWORD);
        assertThat(sealed).doesNotContain(Totp.toBase32(secret));
        assertThat(vault.open(sealed)).isEqualTo(secret);
    }

    @Test
    @DisplayName("C1a-04 a one-time code works once: replaying the same code is refused even with the right password")
    void codeCannotBeReplayed() throws Exception {
        String code = code();
        assertThat(login("breakglass", PASSWORD, code).getResponse().getStatus()).isEqualTo(200);

        assertThat(login("breakglass", PASSWORD, code).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RULE-316 unknown login name, wrong password and wrong code give the same answer")
    void sameErrorEverywhere() throws Exception {
        String unknown = login("nobody", PASSWORD, code()).getResponse().getContentAsString();
        String wrongPassword = login("breakglass", "Wrong!Passphrase#42", code()).getResponse().getContentAsString();
        String wrongCode = login("breakglass", PASSWORD, "000000").getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(wrongPassword).isEqualTo(wrongCode).contains("PLATFORM_IDENTITY_CREDENTIALS_INVALID");
        assertThat(login("nobody", PASSWORD, code()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RULE-316 five failures lock the account for 15 minutes; afterwards it works again")
    void lockout() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login("breakglass", "Wrong!Passphrase#4" + i, code()).getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(jdbc.sql("select locked_until is not null from platform_identity.emergency_admins").query(Boolean.class).single()).isTrue();

        assertThat(login("breakglass", PASSWORD, code()).getResponse().getStatus()).as("locked: even the right credentials fail").isEqualTo(401);
        clock.advance(Duration.ofMinutes(14));
        assertThat(login("breakglass", PASSWORD, code()).getResponse().getStatus()).as("still locked after 14 minutes").isEqualTo(401);
        clock.advance(Duration.ofMinutes(2));

        assertThat(login("breakglass", PASSWORD, code()).getResponse().getStatus()).as("16 minutes later").isEqualTo(200);
        assertThat(jdbc.sql("select failed_attempts from platform_identity.emergency_admins").query(Integer.class).single()).isZero();
        assertThat(recordedIndependently()).extracting(AuditEntry::action).contains("platform.identity.login.failed");
    }

    @Test
    @DisplayName("RULE-316 fewer than five failures do not lock, and success clears the counter")
    void successClearsTheCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("breakglass", "Wrong!Passphrase#4" + i, code());
        }
        assertThat(login("breakglass", PASSWORD, code()).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.sql("select failed_attempts from platform_identity.emergency_admins").query(Integer.class).single()).isZero();
    }

    @Test
    @DisplayName("C1a-04 outside the internal network the endpoint refuses before looking at any credential")
    void internalNetworkOnly() throws Exception {
        MvcResult result = login("breakglass", PASSWORD, code(), "203.0.113.9");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).contains("PLATFORM_IDENTITY_NETWORK_DENIED");
        assertThat(jdbc.sql("select failed_attempts from platform_identity.emergency_admins").query(Integer.class).single())
                .as("outsiders cannot lock the account").isZero();
    }

    @Test
    @DisplayName("C1a-04 a client cannot claim to be internal with a header: only the container-resolved address counts")
    void forwardedHeaderFromAnOutsiderIsIgnored() throws Exception {
        MvcResult result = mvc.perform(post(LOGIN).with(xsrf()).with(request -> {
            request.setRemoteAddr("203.0.113.9");
            return request;
        }).header("X-Forwarded-For", "10.1.2.3").header("X-Real-IP", "10.1.2.3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"breakglass\",\"password\":\"" + PASSWORD + "\",\"totpCode\":\"" + code() + "\"}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("ADR-0027 1.3 a person the snapshot clearly lists as separated cannot use the emergency account")
    void separatedPersonIsRefused() throws Exception {
        knownEmployee(7, EmploymentStatus.SEPARATED);

        MvcResult result = login("breakglass", PASSWORD, code());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).contains("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(cookie(result, "SESSION")).isNull();
    }

    @Test
    @DisplayName("ADR-0027 1.3 a missing snapshot does not stop the login: the organisation source may be down")
    void missingSnapshotIsAllowed() throws Exception {
        when(orgDirectory.findEmployee(new UserId(7))).thenReturn(Optional.empty());

        MvcResult result = login("breakglass", PASSWORD, code());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("administratorModeExpiresAt").asString()).isNotBlank();
    }

    @Test
    @DisplayName("ADR-0027 1.2 the bound person must hold super_admin right now")
    void boundPersonMustHoldSuperAdmin() throws Exception {
        jdbc.sql("delete from platform_identity.account_roles where user_id = 7").update();

        MvcResult result = login("breakglass", PASSWORD, code());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("PLATFORM_IDENTITY_CREDENTIALS_INVALID");
        assertThat(cookie(result, "SESSION")).isNull();
        assertThat(recordedIndependently()).anySatisfy(entry -> assertThat(entry.detail()).contains("no longer holds super_admin"));
    }

    @Test
    @DisplayName("ADR-0027 1.2 a disabled bound account is refused")
    void boundAccountMustBeEnabled() throws Exception {
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where user_id = 7").update();

        MvcResult result = login("breakglass", PASSWORD, code());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).contains("PLATFORM_IDENTITY_ACCOUNT_DISABLED");
    }

    @Test
    @DisplayName("ADR-0027 1.5 the login starts in the administrator mode for one hour, audits and alerts; the second request is still valid")
    void administratorModeForOneHour() throws Exception {
        double loginsBefore = meters.counter("forcitis.platform.identity.emergency.logins").count();
        Instant before = clock.instant();
        MvcResult result = login("breakglass", PASSWORD, code());
        Instant after = clock.instant();
        String session = cookie(result, "SESSION");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(Instant.parse(body(result).path("administratorModeExpiresAt").asString())).isBetween(before.plus(Duration.ofHours(1)),
                after.plus(Duration.ofHours(1)));
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.login.succeeded", "platform.identity.emergency.login");
        assertThat(meters.counter("forcitis.platform.identity.emergency.logins").count()).isEqualTo(loginsBefore + 1);

        var me = body(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn());
        var again = mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn();
        assertThat(me.path("mode").asString()).isEqualTo("ADMINISTRATOR");
        assertThat(Instant.parse(me.path("administratorModeExpiresAt").asString())).isBetween(before.plus(Duration.ofHours(1)),
                after.plus(Duration.ofHours(1)));
        assertThat(again.getResponse().getStatus()).as("no employment check on later requests").isEqualTo(200);
    }

    @Test
    @DisplayName("ADR-0027 1.5 the hour is not extended: afterwards the emergency session is over")
    void administratorModeIsNotExtended() throws Exception {
        String session = cookie(login("breakglass", PASSWORD, code()), "SESSION");
        clock.advance(Duration.ofMinutes(59));
        assertThat(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse()
                .getStatus()).isEqualTo(200);

        clock.advance(Duration.ofMinutes(2));

        assertThat(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse()
                .getStatus()).isEqualTo(401);
        assertThat(count("sessions")).isZero();
    }

    @Test
    @DisplayName("ADR-0027 1.4 within 60 s of super_admin being taken away the emergency session stops working, without an event")
    void sessionEndsWhenSuperAdminIsTakenAway() throws Exception {
        String session = cookie(login("breakglass", PASSWORD, code()), "SESSION");
        assertThat(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
        jdbc.sql("delete from platform_identity.account_roles where user_id = 7").update();

        clock.advance(Duration.ofSeconds(61));

        assertThat(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse()
                .getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("ADR-0027 1.4 within 60 s of the emergency account being disabled its session stops working")
    void sessionEndsWhenTheEmergencyAccountIsDisabled() throws Exception {
        String session = cookie(login("breakglass", PASSWORD, code()), "SESSION");
        assertThat(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
        jdbc.sql("update platform_identity.emergency_admins set is_enabled = false where user_id = 7").update();

        clock.advance(Duration.ofSeconds(61));

        assertThat(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse()
                .getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("ADR-0027 1.5 an emergency session carries the platform permissions only, never a business one")
    void emergencySessionHasPlatformPermissionsOnly() throws Exception {
        String session = cookie(login("breakglass", PASSWORD, code()), "SESSION");

        var me = body(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn());

        assertThat(me.path("permissionCodes").valueStream().map(n -> n.asString()).toList()).containsExactlyInAnyOrder(
                Permission.Codes.PLATFORM_IDENTITY_ROLE_READ, Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
                Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE, Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE);
    }
}
