package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.identity.application.ReconcileEmergencyAdmins;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** With the mode on one usable emergency account is enough; with it off the minimum stays two; the binding rule is the same in both. */
class SingleOperatorEmergencyMinimumIT extends SingleOperatorIdentitySupport {

    @Autowired
    ReconcileEmergencyAdmins reconcile;

    private void emergencyAccount(long id, String loginName) {
        createAccount(id, "super_admin");
        knownEmployee(id, EmploymentStatus.EMPLOYED);
        Result registered = run(attested("register-emergency-admin", employee(id), "--login-name=" + loginName));
        assertThat(registered.exitCode()).as(registered.err()).isZero();
    }

    private void takeSuperAdminFrom(long id) {
        jdbc.sql("""
                update platform_identity.account_role_grants set effective_to_at = greatest(effective_from_at, :at), revocation_cause_kind = 'OPERATIONS',
                       revocation_audit_entry_id = uuidv7(), updated_at = greatest(effective_from_at, :at)
                 where user_id = (select user_id from platform_identity.user_accounts where employee_id = :employee)
                   and role_id = (select role_id from platform_identity.roles where code = 'super_admin') and effective_to_at is null
                """).param("at", java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)).param("employee", TestAccounts.employee(id).value()).update();
    }

    // RULE-428
    @Test
    @DisplayName("SINGLE-OP P2-11 每日核对：模式开启、可用应急账号 1 个 → 无问题、不告警")
    void p211_oneUsableAccountIsEnoughWithTheModeOn() {
        emergencyAccount(7, "breakglass");
        mode(true);

        assertThat(reconcile.execute()).isEmpty();
        assertThat(entries(CHECK_FAILED)).isEmpty();
    }

    // RULE-428
    @Test
    @DisplayName("SINGLE-OP P2-11 每日核对：模式开启、可用 0 个（没有任何应急账号）→ 告警，消息写 at least 1")
    void p211_noneUsableAlertsWithTheModeOn() {
        mode(true);

        List<String> problems = reconcile.execute();

        assertThat(problems).singleElement().asString().contains("only 0 usable emergency account(s)").contains("at least 1 are required").doesNotContain("at least 2");
        assertThat(entries(CHECK_FAILED)).hasSize(1);
    }

    // RULE-429
    @Test
    @DisplayName("SINGLE-OP P2-11 每日核对：模式开启、唯一账号的绑定人已不持有 super_admin → 告警")
    void p211_boundPersonLostSuperAdminAlerts() {
        emergencyAccount(7, "breakglass");
        takeSuperAdminFrom(7);
        mode(true);

        List<String> problems = reconcile.execute();

        assertThat(problems).isNotEmpty();
        assertThat(String.join("; ", problems)).contains("no longer holds super_admin").contains("only 0 usable");
        assertThat(entries(CHECK_FAILED)).hasSize(1);
    }

    @Test
    @DisplayName("SINGLE-OP P2-11 每日核对：模式开启、唯一账号的绑定人账号停用 → 告警")
    void p211_boundPersonAccountDisabledAlerts() {
        emergencyAccount(7, "breakglass");
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where employee_id = :employee").param("employee", TestAccounts.employee(7).value()).update();
        mode(true);

        assertThat(reconcile.execute()).isNotEmpty();
        assertThat(entries(CHECK_FAILED)).hasSize(1);
    }

    // RULE-429
    @Test
    @DisplayName("SINGLE-OP P2-11 每日核对：模式开启、唯一的应急账号本身停用 → 告警")
    void p211_theAccountItselfDisabledAlerts() {
        emergencyAccount(7, "breakglass");
        assertThat(run(attested("disable-emergency-admin", employee(7))).exitCode()).isZero();
        mode(true);

        List<String> problems = reconcile.execute();

        assertThat(problems).singleElement().asString().contains("only 0 usable").contains("at least 1 are required");
    }

    // RULE-429
    @Test
    @DisplayName("SINGLE-OP P2-11 每日核对：模式开启、一个可用加一个绑定人已不持有 super_admin → 仍告警（绑定规则不因下限放宽），消息点名该账号的用户")
    void p211_aBrokenSecondAccountStillAlertsWithTheModeOn() {
        emergencyAccount(7, "breakglass-a");
        emergencyAccount(8, "breakglass-b");
        takeSuperAdminFrom(8);
        mode(true);

        List<String> problems = reconcile.execute();

        assertThat(problems).singleElement().asString().contains("no longer holds super_admin").contains(accountOf(8).toString());
    }

    // RULE-427
    @Test
    @DisplayName("SINGLE-OP P2-12 每日核对：模式关闭、可用 1 个 → 仍告警（下限 2），消息仍写 at least 2")
    void p212_oneUsableAccountStillAlertsWithTheModeOff() {
        emergencyAccount(7, "breakglass");

        List<String> problems = reconcile.execute();

        assertThat(problems).singleElement().asString().contains("only 1 usable emergency account(s)").contains("at least 2 are required");
        assertThat(entries(CHECK_FAILED)).hasSize(1);
    }

    // RULE-427
    @Test
    @DisplayName("SINGLE-OP P2-12 每日核对：模式关闭、可用 0 个 → 告警，消息写 at least 2；两个可用则无问题")
    void p212_noneAlertsAndTwoPassWithTheModeOff() {
        assertThat(reconcile.execute()).singleElement().asString().contains("only 0 usable").contains("at least 2 are required");
        emergencyAccount(7, "breakglass-a");
        emergencyAccount(8, "breakglass-b");

        assertThat(reconcile.execute()).isEmpty();
    }

    private static List<String> withoutTheSecretsAndTheWarning(String out) {
        return Arrays.stream(out.split("\n")).filter(line -> !line.startsWith("WARNING"))
                .map(line -> line.replaceAll("login name \\S+", "login name X").replaceAll("(password\\s+: )\\S+", "$1P").replaceAll("(base32\\)\\s+: )\\S+", "$1S")
                        .replaceAll("otpauth://\\S+", "otpauth://X"))
                .toList();
    }

    // RULE-428
    @Test
    @DisplayName("SINGLE-OP P2-13 登记提示：模式开启登记第 1 个账号后不再提示「至少 2 个」；模式关闭时仍提示；提示文本之外的输出（凭证展示）两种模式下相同")
    void p213_registrationPromptFollowsTheMode() {
        createAccount(7, "super_admin");
        createAccount(8, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        knownEmployee(8, EmploymentStatus.EMPLOYED);

        Result off = run(attested("register-emergency-admin", employee(7), "--login-name=breakglass-off"));
        mode(true);
        Result on = run(alone("register-emergency-admin", employee(8), "--login-name=breakglass-on"));

        assertThat(off.exitCode()).as(off.err()).isZero();
        assertThat(on.exitCode()).as(on.err()).isZero();
        assertThat(off.out()).contains("WARNING: only 1 usable emergency account(s)").contains("at least 2");
        assertThat(on.out()).doesNotContain("at least 2");
        assertThat(withoutTheSecretsAndTheWarning(on.out())).isEqualTo(withoutTheSecretsAndTheWarning(off.out())).hasSizeGreaterThan(3);
    }

    // RULE-428
    @Test
    @DisplayName("SINGLE-OP P2-13 登记提示：再次登记同一登录名时同样按模式提示（开启不含 at least 2，关闭含）")
    void p213_alreadyRegisteredPromptFollowsTheMode() {
        emergencyAccount(7, "breakglass");

        Result off = run(attested("register-emergency-admin", employee(7), "--login-name=breakglass"));
        mode(true);
        Result on = run(alone("register-emergency-admin", employee(7), "--login-name=breakglass"));

        assertThat(off.out()).contains("already registered").contains("at least 2");
        assertThat(on.out()).contains("already registered").doesNotContain("at least 2");
    }

    // RULE-430
    @Test
    @DisplayName("SINGLE-OP P2-14 应急账号只能绑定当前的 super_admin 持有人：模式开启下绑定不持有 super_admin 的人、无账号的人、账号停用的 super_admin 持有人仍被拒，无写入、无标注")
    void p214_bindingStaysRestrictedToSuperAdminHolders() {
        mode(true);
        createAccount(7, "employee");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        createAccount(8, "super_admin");
        knownEmployee(8, EmploymentStatus.EMPLOYED);
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where employee_id = :employee").param("employee", TestAccounts.employee(8).value()).update();
        List<Long> before = identityRows();

        for (long person : new long[] {7, 8, 404}) {
            Result result = run(alone("register-emergency-admin", employee(person), "--login-name=breakglass-" + person));

            assertThat(result.exitCode()).as("employee " + person).isEqualTo(REFUSED);
            assertThat(result.err()).as("employee " + person).contains("PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID");
        }
        assertNothingChanged(before);
    }

    // RULE-430
    @Test
    @DisplayName("SINGLE-OP P2-14 启用也要求绑定人当前持有 super_admin：模式开启下绑定人失去 super_admin 后 enable 仍被拒")
    void p214_enableStillNeedsTheBinding() {
        emergencyAccount(7, "breakglass");
        assertThat(run(attested("disable-emergency-admin", employee(7))).exitCode()).isZero();
        takeSuperAdminFrom(7);
        mode(true);

        Result result = run(alone("enable-emergency-admin", employee(7)));

        assertThat(result.exitCode()).isEqualTo(REFUSED);
        assertThat(result.err()).contains("PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID");
        assertThat(applied()).isEmpty();
    }

    private static final String MARK = "relaxation=EMERGENCY_ACCOUNT_MINIMUM_ONE";

    // RULE-431
    @Test
    @DisplayName("SINGLE-OP P2-11 模式开启且可用恰为 1：每日核对恰写一条标注 platform.identity.single-operator-mode.applied，detail 恰为 relaxation=EMERGENCY_ACCOUNT_MINIMUM_ONE")
    void p211_oneUsableWithTheModeOnWritesExactlyOneMark() {
        emergencyAccount(7, "breakglass");
        mode(true);

        assertThat(reconcile.execute()).isEmpty();

        assertThat(applied()).singleElement().satisfies(entry -> assertThat(entry.detail()).isEqualTo(MARK));
    }

    // RULE-431
    @Test
    @DisplayName("SINGLE-OP P2-11 可用为 0、可用为 2（模式开启）与模式关闭（可用 0、1、2）：每日核对都不写该标注")
    void p211_otherCasesWriteNoMark() {
        mode(true);
        reconcile.execute();
        emergencyAccount(7, "breakglass-a");
        emergencyAccount(8, "breakglass-b");
        mode(true);
        assertThat(reconcile.execute()).isEmpty();
        assertThat(applied()).as("mode on: zero and two usable").isEmpty();

        mode(false);
        reconcile.execute();
        assertThat(applied()).as("mode off: two usable").isEmpty();
        jdbc.sql("delete from platform_identity.emergency_admins where user_id = :user").param("user", accountOf(8)).update();
        reconcile.execute();
        assertThat(applied()).as("mode off: one usable").isEmpty();
        jdbc.sql("delete from platform_identity.emergency_admins").update();
        reconcile.execute();
        assertThat(applied()).as("mode off: none usable").isEmpty();
    }

    static final String ORIGINAL_WARNING = "WARNING: only 1 usable emergency account(s); at least 2, bound to different super_admin holders, are required";

    // RULE-427
    @Test
    @DisplayName("SINGLE-OP P2-13 模式关闭时登记提示的完整文本与 P2 之前逐字相同（含 bound to different super_admin holders）；模式开启时没有任何含 at least 2 的 WARNING 行")
    void p213_theWarningTextIsKeptWithTheModeOff() {
        createAccount(7, "super_admin");
        createAccount(8, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        knownEmployee(8, EmploymentStatus.EMPLOYED);

        Result off = run(attested("register-emergency-admin", employee(7), "--login-name=breakglass-off"));
        Result offAgain = run(attested("register-emergency-admin", employee(7), "--login-name=breakglass-off"));
        mode(true);
        Result on = run(alone("register-emergency-admin", employee(8), "--login-name=breakglass-on"));

        assertThat(off.out().split("\n")).contains(ORIGINAL_WARNING);
        assertThat(offAgain.out().split("\n")).contains(ORIGINAL_WARNING);
        assertThat(on.out().split("\n")).noneMatch(line -> line.startsWith("WARNING") && line.contains("at least 2"));
    }
}
