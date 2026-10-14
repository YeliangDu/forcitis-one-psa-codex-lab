package com.forcitis.one.platform.identity;

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

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("OperationsCommands: ADR-0027 section 1, D-1a-45..48 (emergency accounts and the first administrators)")
class OperationsCommandsIntegrationTest extends IdentityIntegrationTest {

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
        String[] base = {"--operation=" + operation, "--operator=alice", "--witness=bob"};
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

        Result result = run(with("register-emergency-admin", "--user-id=7", "--login-name=breakglass"));

        assertThat(result.exitCode()).isZero();
        String password = find(PASSWORD, result.out());
        String secret = find(SECRET, result.out());
        assertThat(result.out()).contains("WARNING: only 1 usable emergency account");
        assertThat(jdbc.sql("select password_hash from platform_identity.emergency_admins where user_id = 7").query(String.class).single())
                .startsWith("$argon2id$").doesNotContain(password);
        assertThat(jdbc.sql("select totp_secret_encrypted from platform_identity.emergency_admins where user_id = 7").query(String.class).single())
                .doesNotContain(secret);
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.operations.emergency-admin.registered")).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.detail()).contains("operator=alice", "witness=bob", "loginName=breakglass").doesNotContain(password).doesNotContain(secret);
                    assertThat(entry.subjectKey()).isEqualTo("emergency-admin:7");
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
        run(with("register-emergency-admin", "--user-id=7", "--login-name=breakglass"));
        String hash = jdbc.sql("select password_hash from platform_identity.emergency_admins where user_id = 7").query(String.class).single();

        Result again = run(with("register-emergency-admin", "--user-id=7", "--login-name=breakglass"));

        assertThat(again.exitCode()).isZero();
        assertThat(again.out()).contains("already registered").doesNotContain("password");
        assertThat(jdbc.sql("select password_hash from platform_identity.emergency_admins where user_id = 7").query(String.class).single()).isEqualTo(hash);
        assertThat(count("emergency_admins")).isEqualTo(1);
        Result other = run(with("register-emergency-admin", "--user-id=7", "--login-name=another"));
        assertThat(other.exitCode()).isEqualTo(1);
        assertThat(other.err()).contains("PLATFORM_IDENTITY_EMERGENCY_ADMIN_DUPLICATE");
    }

    @Test
    @DisplayName("an emergency account is bound to someone who holds super_admin now; two accounts bound to different holders satisfy the minimum")
    void bindingAndMinimum() {
        createAccount(7, "employee");
        createAccount(8, "super_admin");
        createAccount(9, "super_admin");

        Result notHolder = run(with("register-emergency-admin", "--user-id=7", "--login-name=breakglass-a"));
        Result noAccount = run(with("register-emergency-admin", "--user-id=404", "--login-name=breakglass-b"));
        run(with("register-emergency-admin", "--user-id=8", "--login-name=breakglass-c"));
        Result second = run(with("register-emergency-admin", "--user-id=9", "--login-name=breakglass-d"));

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

        assertThat(run("--operation=register-emergency-admin", "--user-id=7", "--login-name=breakglass", "--operator=alice").exitCode()).isEqualTo(1);
        assertThat(run("--operation=register-emergency-admin", "--user-id=7", "--login-name=breakglass", "--operator=alice", "--witness=alice").err())
                .contains("PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED");
        assertThat(run("--user-id=7").exitCode()).as("no operation named").isEqualTo(2);
        assertThat(run(with("register-emergency-admin", "--login-name=breakglass")).exitCode()).as("no person named").isEqualTo(2);
        assertThat(run(with("no-such-operation", "--user-id=7")).exitCode()).isEqualTo(2);
        assertThat(count("emergency_admins")).isZero();
    }

    @Test
    @DisplayName("resetting issues new credentials, the old ones stop working and the person's sessions end")
    void resetReplacesTheCredentials() throws Exception {
        createAccount(7, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        Result registered = run(with("register-emergency-admin", "--user-id=7", "--login-name=breakglass"));
        String oldPassword = find(PASSWORD, registered.out());
        String oldSecret = find(SECRET, registered.out());
        MvcResult first = emergencyLogin("breakglass", oldPassword, Totp.code(Totp.fromBase32(oldSecret), Totp.stepAt(clock.instant())));
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        Result reset = run(with("reset-emergency-admin-credentials", "--user-id=7"));

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
        assertThat(run(with("reset-emergency-admin-credentials", "--user-id=999")).err()).contains("PLATFORM_IDENTITY_EMERGENCY_ADMIN_NOT_FOUND");
    }

    @Test
    @DisplayName("disabling and enabling are idempotent, audited, and enabling needs the bound person to hold super_admin")
    void disableAndEnable() {
        createAccount(7, "super_admin");
        run(with("register-emergency-admin", "--user-id=7", "--login-name=breakglass"));

        assertThat(run(with("disable-emergency-admin", "--user-id=7")).out()).isEqualTo("disabled\n");
        assertThat(run(with("disable-emergency-admin", "--user-id=7")).out()).contains("already disabled");
        jdbc.sql("delete from platform_identity.account_roles where user_id = 7").update();
        Result refused = run(with("enable-emergency-admin", "--user-id=7"));
        assertThat(refused.exitCode()).isEqualTo(1);
        assertThat(refused.err()).contains("PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID");
        createAccount(7, "super_admin");
        assertThat(run(with("enable-emergency-admin", "--user-id=7")).out()).isEqualTo("enabled\n");
        assertThat(run(with("enable-emergency-admin", "--user-id=7")).out()).contains("already enabled");
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.operations.emergency-admin.disabled",
                "platform.identity.operations.emergency-admin.enabled");
    }

    @Test
    @DisplayName("granting a role opens the account of an employed person, audits it, and granting it again changes nothing")
    void grantRole() {
        knownEmployee(33, EmploymentStatus.EMPLOYED);

        Result first = run(with("grant-role", "--user-id=33", "--role-code=business_admin"));
        Result again = run(with("grant-role", "--user-id=33", "--role-code=business_admin"));

        assertThat(first.exitCode()).isZero();
        assertThat(first.out()).isEqualTo("role granted\n");
        assertThat(again.out()).contains("already held");
        assertThat(jdbc.sql("""
                select r.code from platform_identity.account_roles ar join platform_identity.roles r on r.role_id = ar.role_id
                 where ar.user_id = 33 order by r.code
                """).query(String.class).list()).containsExactly("business_admin", "employee");
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.operations.account.role.granted")).singleElement()
                .satisfies(entry -> assertThat(entry.detail()).contains("operator=alice", "witness=bob", "role=business_admin"));
        assertThat(run(with("grant-role", "--user-id=33", "--role-code=no_such_role")).err()).contains("PLATFORM_IDENTITY_ROLE_NOT_FOUND");
        assertThat(run(with("grant-role", "--user-id=34", "--role-code=super_admin")).err()).contains("PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("the first super_admin holder can be granted, then bound to an emergency account: the recovery path after a lock-out")
    void recoveryAfterALockOut() {
        knownEmployee(35, EmploymentStatus.EMPLOYED);

        assertThat(run(with("grant-role", "--user-id=35", "--role-code=super_admin")).exitCode()).isZero();
        Result registered = run(with("register-emergency-admin", "--user-id=35", "--login-name=breakglass"));

        assertThat(registered.exitCode()).isZero();
        assertThat(registered.out()).contains("emergency account registered");
    }
}
