package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import com.forcitis.one.platform.identity.domain.Totp;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("OperationsCommands: ADR-0027 section 1, D-1a-45..48 (emergency accounts and the first administrators)")
class OperationsCommandsIntegrationTest extends IdentityIntegrationTest {
    static final String OPERATOR_ID = "01900000-0000-7000-8000-00000000a001";
    static final String WITNESS_ID = "01900000-0000-7000-8000-00000000a002";

    static final Pattern PASSWORD = Pattern.compile("password\\s+: (\\S+)");
    static final Pattern SECRET = Pattern.compile("\\(base32\\)\\s+: (\\S+)");

    @Autowired
    OperationsCommands commands;

    record Result(int exitCode, String out, String err) {
    }

    private Result run(String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = commands.run(new DefaultApplicationArguments(arguments), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private String[] with(String operation, String... more) {
        String[] base = {"--operation=" + operation, "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID};
        String[] all = Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return all;
    }

    private MvcResult emergencyLogin(String name, String password, String code) throws Exception {
        return mvc.perform(post(EmergencyLoginIntegrationTest.LOGIN).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"loginName\":\"" + name + "\",\"password\":\"" + password + "\",\"totpCode\":\"" + code + "\"}")).andReturn();
    }

    private static String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        assertThat(matcher.find()).as(text).isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("registering gives a working password and one-time-code secret, shown once, stored only hashed and sealed, audited with operator and witness")
    void registerIssuesCredentialsThatWork(CapturedOutput logs) throws Exception {
        createAccount(7, "super_admin");

        Result result = run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=breakglass"));

        assertThat(result.exitCode()).isZero();
        String password = find(PASSWORD, result.out());
        String secret = find(SECRET, result.out());
        assertThat(result.out()).contains("WARNING: only 1 usable emergency account");
        assertThat(jdbc.sql("select password_hash from platform_identity.emergency_admins where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(7) + ")").query(String.class).single())
                .startsWith("$argon2id$").doesNotContain(password);
        assertThat(jdbc.sql("select totp_secret_encrypted from platform_identity.emergency_admins where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(7) + ")").query(String.class).single())
                .doesNotContain(secret);
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.operations.emergency-admin.registered")).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.detail()).contains("operatorId=" + OPERATOR_ID, "witnessId=" + WITNESS_ID).doesNotContain("loginName=", "breakglass").doesNotContain(password).doesNotContain(secret);
                    assertThat(entry.subject()).isEqualTo(AuditSubject.of(AuditSubjectKind.EMERGENCY_ADMIN, accountOf(7)));
                });
        assertThat(logs.getAll()).as("a secret never reaches a log line").doesNotContain(password).doesNotContain(secret);

        knownEmployee(7, EmploymentStatus.EMPLOYED);
        MvcResult login = emergencyLogin("breakglass", password, Totp.code(Totp.fromBase32(secret), Totp.stepAt(clock.instant())));
        assertThat(login.getResponse().getStatus()).as("the issued credentials open the emergency login").isEqualTo(200);
    }

    @Test
    @DisplayName("running the registration again changes nothing and issues nothing; another login name for the same person is refused")
    void registerIsIdempotent() {
        createAccount(7, "super_admin");
        run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=breakglass"));
        String hash = jdbc.sql("select password_hash from platform_identity.emergency_admins where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(7) + ")").query(String.class).single();

        Result again = run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=breakglass"));

        assertThat(again.exitCode()).isZero();
        assertThat(again.out()).contains("already registered").doesNotContain("password");
        assertThat(jdbc.sql("select password_hash from platform_identity.emergency_admins where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(7) + ")").query(String.class).single()).isEqualTo(hash);
        assertThat(count("emergency_admins")).isEqualTo(1);
        Result other = run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=another"));
        assertThat(other.exitCode()).isEqualTo(1);
        assertThat(other.err()).contains("PLATFORM_IDENTITY_EMERGENCY_ADMIN_DUPLICATE");
    }

    @Test
    @DisplayName("an emergency account is bound to someone who holds super_admin now; two accounts bound to different holders satisfy the minimum")
    void bindingAndMinimum() {
        createAccount(7, "employee");
        createAccount(8, "super_admin");
        createAccount(9, "super_admin");

        Result notHolder = run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=breakglass-a"));
        Result noAccount = run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(404).value(), "--login-name=breakglass-b"));
        run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(8).value(), "--login-name=breakglass-c"));
        Result second = run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(9).value(), "--login-name=breakglass-d"));

        assertThat(notHolder.exitCode()).isEqualTo(1);
        assertThat(notHolder.err()).contains("PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID");
        assertThat(noAccount.err()).contains("PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID");
        assertThat(second.exitCode()).isZero();
        assertThat(second.out()).doesNotContain("WARNING");
        assertThat(count("emergency_admins")).isEqualTo(2);
    }

    @Test
    @DisplayName("every command needs an operator and a different witness; without them nothing happens")
    void witnessIsRequired() {
        createAccount(7, "super_admin");

        assertThat(run("--operation=register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=breakglass", "--operator=" + OPERATOR_ID).exitCode()).isEqualTo(1);
        assertThat(run("--operation=register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=breakglass", "--operator=" + OPERATOR_ID, "--witness=" + OPERATOR_ID).err())
                .contains("PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED");
        assertThat(run("--employee-id=" + TestAccounts.employee(7).value()).exitCode()).as("no operation named").isEqualTo(2);
        assertThat(run(with("register-emergency-admin", "--login-name=breakglass")).exitCode()).as("no person named").isEqualTo(2);
        assertThat(run(with("no-such-operation", "--employee-id=" + TestAccounts.employee(7).value())).exitCode()).isEqualTo(2);
        assertThat(count("emergency_admins")).isZero();
    }

    @Test
    @DisplayName("R-1b P3b the person is named by the employee's own id: a number, an upper-case or a malformed id is a usage error and nothing happens")
    void employeeIdIsTheCanonicalUuid() {
        createAccount(7, "super_admin");

        for (String wrong : new String[] {"7", TestAccounts.employee(7).value().toString().toUpperCase(), "not-an-id", ""}) {
            Result result = run(with("register-emergency-admin", "--employee-id=" + wrong, "--login-name=breakglass"));
            assertThat(result.exitCode()).as(wrong).isEqualTo(2);
            assertThat(result.err()).as(wrong).contains("--employee-id");
        }
        assertThat(count("emergency_admins")).isZero();
    }

    @Test
    @DisplayName("resetting issues new credentials, the old ones stop working and the person's sessions end")
    void resetReplacesTheCredentials() throws Exception {
        createAccount(7, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        Result registered = run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=breakglass"));
        String oldPassword = find(PASSWORD, registered.out());
        String oldSecret = find(SECRET, registered.out());
        MvcResult first = emergencyLogin("breakglass", oldPassword, Totp.code(Totp.fromBase32(oldSecret), Totp.stepAt(clock.instant())));
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        Result reset = run(with("reset-emergency-admin-credentials", "--employee-id=" + TestAccounts.employee(7).value()));

        assertThat(reset.exitCode()).isZero();
        String newPassword = find(PASSWORD, reset.out());
        String newSecret = find(SECRET, reset.out());
        assertThat(newPassword).isNotEqualTo(oldPassword);
        assertThat(count("sessions")).as("the person's sessions end").isZero();
        assertThat(emergencyLogin("breakglass", oldPassword, Totp.code(Totp.fromBase32(oldSecret), Totp.stepAt(clock.instant()) + 1)).getResponse().getStatus())
                .isEqualTo(401);
        clock.advance(Duration.ofMinutes(20));
        assertThat(emergencyLogin("breakglass", newPassword, Totp.code(Totp.fromBase32(newSecret), Totp.stepAt(clock.instant()))).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.operations.emergency-admin.credentials-reset");
        assertThat(run(with("reset-emergency-admin-credentials", "--employee-id=" + TestAccounts.employee(999).value())).err()).contains("PLATFORM_IDENTITY_EMERGENCY_ADMIN_NOT_FOUND");
    }

    @Test
    @DisplayName("disabling and enabling are idempotent, audited, and enabling needs the bound person to hold super_admin")
    void disableAndEnable() {
        createAccount(7, "super_admin");
        run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value(), "--login-name=breakglass"));

        assertThat(run(with("disable-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value())).out()).isEqualTo("disabled\n");
        assertThat(run(with("disable-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value())).out()).contains("already disabled");
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null and user_id = (select user_id from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(7) + ")").update();
        Result refused = run(with("enable-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value()));
        assertThat(refused.exitCode()).isEqualTo(1);
        assertThat(refused.err()).contains("PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID");
        createAccount(7, "super_admin");
        assertThat(run(with("enable-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value())).out()).isEqualTo("enabled\n");
        assertThat(run(with("enable-emergency-admin", "--employee-id=" + TestAccounts.employee(7).value())).out()).contains("already enabled");
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.operations.emergency-admin.disabled",
                "platform.identity.operations.emergency-admin.enabled");
    }

    @Test
    @DisplayName("granting a role opens the account of an employed person, audits it, and granting it again changes nothing")
    void grantRole() {
        knownEmployee(33, EmploymentStatus.EMPLOYED);

        Result first = run(with("grant-role", "--employee-id=" + TestAccounts.employee(33).value(), "--role-code=business_admin"));
        Result again = run(with("grant-role", "--employee-id=" + TestAccounts.employee(33).value(), "--role-code=business_admin"));

        assertThat(first.exitCode()).isZero();
        assertThat(first.out()).isEqualTo("role granted\n");
        assertThat(again.out()).contains("already held");
        assertThat(jdbc.sql("""
                select r.code from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id
                 where ar.effective_to_at is null and ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :employee) order by r.code
                """).param("employee", TestAccounts.employee(33).value()).query(String.class).list()).containsExactly("business_admin", "employee");
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.operations.account.role.granted")).singleElement()
                .satisfies(entry -> assertThat(entry.detail()).contains("operatorId=" + OPERATOR_ID, "witnessId=" + WITNESS_ID, "role=business_admin"));
        assertThat(run(with("grant-role", "--employee-id=" + TestAccounts.employee(33).value(), "--role-code=no_such_role")).err()).contains("PLATFORM_IDENTITY_ROLE_NOT_FOUND");
        assertThat(run(with("grant-role", "--employee-id=" + TestAccounts.employee(34).value(), "--role-code=super_admin")).err()).contains("PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("1333 granting a role to a person who has left is refused: no grant row, no account version move (TLA rule 5, no grant to a leaver)")
    void grantRoleToALeaverIsRefused() {
        knownEmployee(36, EmploymentStatus.EMPLOYED);
        assertThat(run(with("grant-role", "--employee-id=" + TestAccounts.employee(36).value(), "--role-code=employee")).exitCode()).isZero();
        String versionBefore = accountVersion(36);
        long grantsBefore = grantRows(36);
        knownEmployee(36, EmploymentStatus.SEPARATED);

        Result refused = run(with("grant-role", "--employee-id=" + TestAccounts.employee(36).value(), "--role-code=business_admin"));

        assertThat(refused.exitCode()).isNotZero();
        assertThat(refused.err()).contains("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(grantRows(36)).isEqualTo(grantsBefore);
        assertThat(accountVersion(36)).isEqualTo(versionBefore);
    }

    private long grantRows(long employee) {
        return jdbc.sql("select count(*) from platform_identity.account_role_grants where user_id = (select user_id from platform_identity.user_accounts where employee_id = :e)")
                .param("e", TestAccounts.employee(employee).value()).query(Long.class).single();
    }

    @Test
    @DisplayName("the first super_admin holder can be granted, then bound to an emergency account: the recovery path after a lock-out")
    void recoveryAfterALockOut() {
        knownEmployee(35, EmploymentStatus.EMPLOYED);

        assertThat(run(with("grant-role", "--employee-id=" + TestAccounts.employee(35).value(), "--role-code=super_admin")).exitCode()).isZero();
        Result registered = run(with("register-emergency-admin", "--employee-id=" + TestAccounts.employee(35).value(), "--login-name=breakglass"));

        assertThat(registered.exitCode()).isZero();
        assertThat(registered.out()).contains("emergency account registered");
    }
}
