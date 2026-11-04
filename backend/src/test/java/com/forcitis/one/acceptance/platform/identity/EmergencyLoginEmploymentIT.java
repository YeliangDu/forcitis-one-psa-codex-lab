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
 * finds the employee behind the bound account through identity and then asks workforce whether that employee is employed. A snapshot that
 * works but does not list that employee refuses the login and alerts.
 * <p>#934 (lead ruling 2026-11-02 on point 5): after D2 there is no snapshot to be fresh or missing; employment is judged from the employee
 * versions in force on the business day, so the freshness condition of ADR-0027 section 1 item 3 is obsolete. A person employed by versions
 * may log in by emergency login, also when the snapshot row says separated; a person separated by versions may not, whatever the snapshot says;
 * a person with neither versions nor a snapshot row is not listed and is refused (403 PLATFORM_IDENTITY_EMPLOYEE_SEPARATED). The pass-through
 * when the snapshot is unavailable and the stale-snapshot case went with the old sync; what replaces "unavailable" is the D1 card's.
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
        boolean oldSync = oldSyncTablesExist();
        try (Connection workforce = schemaConnection("organization_workforce"); Statement statement = workforce.createStatement()) {
            if (oldSync) {
                statement.execute("delete from sync_run_held_separations");
            }
            statement.execute("delete from assignment_versions");
            statement.execute("delete from employee_versions");
            statement.execute("delete from assignments");
            statement.execute("delete from employees");
            statement.execute("delete from org_units");
            if (oldSync) {
                statement.execute("delete from sync_runs");
            }
        }
    }

    /** One employee version of the person from the day on: EMPLOYED (regular, fee earner no) or SEPARATED; closed on the day before {@code to} when given. */
    private void employeeVersion(UUID person, String status, java.time.LocalDate from, java.time.LocalDate to) throws SQLException {
        boolean employed = "EMPLOYED".equals(status);
        java.time.OffsetDateTime at = java.time.OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce"); java.sql.PreparedStatement insert = workforce.prepareStatement("""
                insert into employee_versions (employee_version_id, employee_id, effective_from_date, effective_to_date, closed_at, revision_number,
                  employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                  change_channel_kind, created_at, updated_at)
                values (?, ?, ?, ?, ?, 0, ?, ?, false, false, ?, ?, 'MIGRATION', ?, ?)""")) {
            insert.setObject(1, com.forcitis.one.kernel.Uuids.timeOrdered(NOW));
            insert.setObject(2, stored(person));
            insert.setObject(3, from);
            insert.setObject(4, to);
            insert.setObject(5, to == null ? null : at);
            insert.setString(6, status);
            insert.setString(7, employed ? "REGULAR" : null);
            insert.setObject(8, employed ? from : null);
            insert.setString(9, employed ? "LEGACY_HR_RECORD" : null);
            insert.setObject(10, at);
            insert.setObject(11, at);
            insert.executeUpdate();
        }
    }

    private String snapshotStatus(UUID person) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce");
             java.sql.PreparedStatement select = workforce.prepareStatement("select employment_status from employees where id = ?")) {
            select.setObject(1, stored(person));
            try (java.sql.ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static String errorCode(MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString();
        Matcher code = Pattern.compile("\"errorCode\"\\s*:\\s*\"([A-Z_]+)\"").matcher(body);
        return code.find() ? code.group(1) : body;
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

    // RULE-420
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

    // RULE-420
    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 7 条 / ADR-0027 第 1 项第 3 条（对照）：快照可用且绑定员工在职 → 放行，进入管理模式")
    void aWorkingSnapshotWithTheBoundEmployeeEmployedLetsThrough() throws Exception {
        UUID unit = workingSnapshot();
        UUID bound = newEmployeeId();
        EmergencyAccount account = boundSuperAdministrator(bound, unit);
        employeeVersion(bound, "EMPLOYED", TODAY.minusYears(1), null);

        MockHttpServletResponse login = emergencyLogin(account);

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        assertThat(startedASession(login)).isTrue();
        assertThat(login.getContentAsString()).contains("administratorModeExpiresAt");
    }

    @Test
    @DisplayName("#934 裁定 5：既无员工版本、也无快照行的绑定员工 → 查无此人，拒绝应急登录（403 PLATFORM_IDENTITY_EMPLOYEE_SEPARATED），不建会话")
    void neitherVersionsNorASnapshotRowRefuses() throws Exception {
        UUID unit = workingSnapshot();
        EmergencyAccount account = boundSuperAdministrator(newEmployeeId(), unit);

        clearOrgSnapshot();
        clock.set(NOW.plus(Duration.ofSeconds(31)));
        MockHttpServletResponse login = emergencyLogin(account);

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(403);
        assertThat(errorCode(login)).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(startedASession(login)).as("a session was started").isFalse();
    }

    @Test
    @DisplayName("#934 裁定 5：按员工版本当天在职、快照却写已离职 → 放行应急登录，进入管理模式")
    void employedByVersionsLetsThroughWhateverTheSnapshotSays() throws Exception {
        UUID unit = workingSnapshot();
        UUID bound = newEmployeeId();
        EmergencyAccount account = boundSuperAdministrator(bound, unit);
        employeeVersion(bound, "EMPLOYED", TODAY.minusYears(1), null);
        try (Connection workforce = schemaConnection("organization_workforce");
             java.sql.PreparedStatement separate = workforce.prepareStatement("update employees set employment_status = 'SEPARATED', separated_at = ? where id = ?")) {
            separate.setObject(1, java.time.OffsetDateTime.ofInstant(NOW.minus(Duration.ofDays(1)), java.time.ZoneOffset.UTC));
            separate.setObject(2, stored(bound));
            separate.executeUpdate();
        }
        assertThat(snapshotStatus(bound)).as("premise: the snapshot row says separated").isEqualTo("SEPARATED");

        MockHttpServletResponse login = emergencyLogin(account);

        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        assertThat(startedASession(login)).isTrue();
        assertThat(login.getContentAsString()).contains("administratorModeExpiresAt");
    }

    @Test
    @DisplayName("#934 裁定 5：按员工版本当天已离职、快照却仍写在职 → 拒绝应急登录，不建会话")
    void separatedByVersionsRefusesWhateverTheSnapshotSays() throws Exception {
        UUID unit = workingSnapshot();
        UUID bound = newEmployeeId();
        EmergencyAccount account = boundSuperAdministrator(bound, unit);
        employeeVersion(bound, "EMPLOYED", TODAY.minusYears(1), TODAY.minusDays(1));
        employeeVersion(bound, "SEPARATED", TODAY.minusDays(1), null);
        assertThat(snapshotStatus(bound)).as("premise: the snapshot row still says employed").isEqualTo("EMPLOYED");

        MockHttpServletResponse login = emergencyLogin(account);

        assertThat(login.getStatus()).as(login.getContentAsString()).isIn(401, 403);
        assertThat(startedASession(login)).as("a session was started").isFalse();
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
