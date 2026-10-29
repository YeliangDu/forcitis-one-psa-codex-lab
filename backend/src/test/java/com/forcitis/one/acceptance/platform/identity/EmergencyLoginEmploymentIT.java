package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 (ADR-0033 section 3 item 7, U-4 answered "拒绝并告警"; ADR-0027 section 1 item 3): the employment check of the emergency login
 * finds the employee behind the bound account through identity and then reads the organisation snapshot. A snapshot that works but does
 * not list that employee refuses the login and alerts; a snapshot that is not there at all, or is stale because the source has been
 * failing, lets the login through and alerts on top of the usual emergency-login alert.
 * The bound account's id and its employee id differ (the account id is a UUID), so the check has to be made on the employee's side.
 * The emergency account is registered with the operations command; its password and one-time-code secret exist only in this test's memory.
 */
class EmergencyLoginEmploymentIT extends AccountsAcceptanceSupport {

    static final String EMERGENCY_LOGIN = "/api/v1/platform/sessions:emergency-login";
    static final Pattern PASSWORD = Pattern.compile("(?m)^password\\s*: (\\S+)$");
    static final Pattern SECRET = Pattern.compile("(?m)^one-time-code secret \\(base32\\)\\s*: ([A-Z2-7]+)$");

    static final TestDatabase postgres = TestPostgres.database(EmergencyLoginEmploymentIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** A throw-away encryption key for the one-time-code secrets, generated per run. */
    @DynamicPropertySource
    static void totpKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        registry.add("forcitis.platform.identity.emergency.totp-encryption-key", () -> encoded);
    }

    @Autowired
    OperationsCommands operations;

    record EmergencyAccount(String loginName, String password, byte[] secret) {
    }

    /** A person who is employed, signs in once (which opens the account), holds super_admin and gets an emergency account. */
    private EmergencyAccount boundSuperAdministrator(UUID person, UUID unit) throws Exception {
        employee(person, unit, null);
        SignedIn account = signIn(person);
        assertThat(employeeOfAccount(account.userId())).isEqualTo(person);
        grantRole(account.userId(), "super_admin");
        return register(account, "breakglass-" + serialOf(person));
    }

    /**
     * Registers through the operations command. P2 names the person either by the account (--user-id, a UUID) or by the employee
     * (--employee-id, ADR-0033 section 3 item 6); a "wrong usage" answer to the first form tries the second.
     */
    private EmergencyAccount register(SignedIn account, String loginName) {
        List<String> forms = List.of("--user-id=" + account.userId(), "--employee-id=" + text(account.employeeId()));
        StringBuilder refusals = new StringBuilder();
        for (String person : forms) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            ensureAttestors();
            int exit = operations.run(new DefaultApplicationArguments("--operation=register-emergency-admin", person, "--login-name=" + loginName,
                    "--operator=01900000-0000-7000-8000-00000000a001", "--witness=01900000-0000-7000-8000-00000000a002"), new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            if (exit == 0) {
                String shown = out.toString(StandardCharsets.UTF_8);
                Matcher password = PASSWORD.matcher(shown);
                Matcher secret = SECRET.matcher(shown);
                if (!password.find() || !secret.find()) {
                    throw new AssertionError("the register command did not show the credentials");
                }
                return new EmergencyAccount(loginName, password.group(1), base32(secret.group(1)));
            }
            refusals.append(person.substring(0, person.indexOf('='))).append(" -> exit ").append(exit).append(": ")
                    .append(err.toString(StandardCharsets.UTF_8).strip()).append("; ");
            if (exit != 2) {
                break;
            }
        }
        throw new AssertionError("registering the emergency account failed: " + refusals);
    }

    private MockHttpServletResponse emergencyLogin(EmergencyAccount account) throws Exception {
        String code = totp(account.secret(), clock.instant());
        return mvc.perform(post(EMERGENCY_LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"loginName\":\"" + account.loginName()
                + "\",\"password\":\"" + account.password() + "\",\"totpCode\":\"" + code + "\"}")).andReturn().getResponse();
    }

    private static boolean startedASession(MockHttpServletResponse response) {
        return response.getHeaders("Set-Cookie").stream().anyMatch(header -> header.startsWith("SESSION="));
    }

    private void clearOrgSnapshot() throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); Statement statement = workforce.createStatement()) {
            statement.execute("delete from sync_run_held_separations");
            statement.execute("delete from assignments");
            statement.execute("delete from employees");
            statement.execute("delete from org_units");
            statement.execute("delete from sync_runs");
        }
    }

    /** A working snapshot: a run that succeeded a minute ago and a colleague in it. */
    private UUID workingSnapshot() throws Exception {
        clearOrgSnapshot();
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        employee(newEmployeeId(), unit, null);
        syncRun("SUCCEEDED", NOW.minus(Duration.ofMinutes(2)), NOW.minus(Duration.ofMinutes(1)));
        return unit;
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 7 条（U-4 已答：拒绝并告警）：快照可用但查不到绑定账号的员工 → 拒绝应急登录、不建会话、记登录失败并告警")
    void aWorkingSnapshotWithoutTheBoundEmployeeRefuses() throws Exception {
        UUID unit = workingSnapshot();
        UUID bound = newEmployeeId();
        EmergencyAccount account = boundSuperAdministrator(bound, unit);
        removeFromSnapshot(bound);
        int mark = logMark();

        MockHttpServletResponse login = emergencyLogin(account);

        assertThat(login.getStatus()).as(login.getContentAsString()).isIn(401, 403);
        assertThat(startedASession(login)).as("a session was started").isFalse();
        assertThat(alertsSince(mark)).as("alerts raised by the refused emergency login").isNotEmpty();
        assertThat(auditedActions("platform.identity.login.failed")).isNotEmpty();
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 7 条 / ADR-0027 第 1 项第 3 条（对照）：快照可用且绑定员工在职 → 放行，进入管理模式")
    void aWorkingSnapshotWithTheBoundEmployeeEmployedLetsThrough() throws Exception {
        UUID unit = workingSnapshot();
        EmergencyAccount account = boundSuperAdministrator(newEmployeeId(), unit);

        MockHttpServletResponse login = emergencyLogin(account);

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        assertThat(startedASession(login)).isTrue();
        assertThat(login.getContentAsString()).contains("administratorModeExpiresAt");
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 7 条 / ADR-0027 第 1 项第 3 条：快照整体不可用（没有快照、没有成功过的同步）→ 按 ADR-0027 放行，并比正常的应急登录多告警")
    void noSnapshotAtAllLetsThroughAndAlerts() throws Exception {
        UUID unit = workingSnapshot();
        EmergencyAccount account = boundSuperAdministrator(newEmployeeId(), unit);
        int usualMark = logMark();
        MockHttpServletResponse usual = emergencyLogin(account);
        assertThat(usual.getStatus()).as(usual.getContentAsString()).isEqualTo(200);
        int usualAlerts = alertsSince(usualMark).size();

        clearOrgSnapshot();
        clock.set(NOW.plus(Duration.ofSeconds(31)));
        int mark = logMark();
        MockHttpServletResponse login = emergencyLogin(account);

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        assertThat(startedASession(login)).isTrue();
        assertThat(alertsSince(mark).size()).as("alerts when the snapshot is missing, against %s for a usual emergency login", usualAlerts)
                .isGreaterThan(usualAlerts);
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 7 条 / ADR-0027 第 1 项第 3 条：快照过期（最近一次成功同步在 30 天前，其后同步一直失败）且查不到绑定员工 → 放行，并比正常的应急登录多告警")
    void aStaleSnapshotWithoutTheBoundEmployeeLetsThroughAndAlerts() throws Exception {
        UUID unit = workingSnapshot();
        UUID bound = newEmployeeId();
        EmergencyAccount account = boundSuperAdministrator(bound, unit);
        int usualMark = logMark();
        MockHttpServletResponse usual = emergencyLogin(account);
        assertThat(usual.getStatus()).as(usual.getContentAsString()).isEqualTo(200);
        int usualAlerts = alertsSince(usualMark).size();

        try (Connection workforce = schemaConnection("organization_workforce"); Statement statement = workforce.createStatement()) {
            statement.execute("delete from sync_runs");
        }
        syncRun("SUCCEEDED", NOW.minus(Duration.ofDays(30)), NOW.minus(Duration.ofDays(30)).plusSeconds(60));
        syncRun("FAILED", NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofMinutes(9)));
        syncRun("FAILED", NOW.minus(Duration.ofMinutes(5)), NOW.minus(Duration.ofMinutes(4)));
        removeFromSnapshot(bound);
        clock.set(NOW.plus(Duration.ofSeconds(31)));
        int mark = logMark();
        MockHttpServletResponse login = emergencyLogin(account);

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        assertThat(startedASession(login)).isTrue();
        assertThat(alertsSince(mark).size()).as("alerts when the snapshot is stale, against %s for a usual emergency login", usualAlerts)
                .isGreaterThan(usualAlerts);
    }

    /** RFC 6238 as an authenticator app computes it: HMAC-SHA1, 30-second steps, 6 digits. */
    static String totp(byte[] secret, Instant at) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(Math.floorDiv(at.getEpochSecond(), 30)).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
    }

    static byte[] base32(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
