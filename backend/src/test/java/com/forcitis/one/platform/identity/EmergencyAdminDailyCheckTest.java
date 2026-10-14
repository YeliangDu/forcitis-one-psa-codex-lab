package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.application.ReconcileEmergencyAdmins;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("EmergencyAccountDailyCheck: ADR-0027 section 1 item 8")
class EmergencyAdminDailyCheckTest extends IdentityIntegrationTest {

    @Autowired
    ReconcileEmergencyAdmins check;

    @Autowired
    EmergencyAdminRepository admins;

    @Autowired
    PasswordHasher hasher;

    @Autowired
    TotpSecretVault vault;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    MeterRegistry meters;

    private void emergencyAccount(long userId, String name, String... roles) {
        createAccount(userId, roles);
        admins.insertIfAbsent(EmergencyAdmin.register(new UserId(userId), name, hasher.hash("Str0ng!Passphrase#42"), vault.seal(Totp.newSecret())));
    }

    private List<String> run() {
        return tx.execute(status -> check.execute());
    }

    @Test
    @DisplayName("two usable emergency accounts bound to different super_admin holders: nothing to report")
    void twoUsableAccountsPass() {
        emergencyAccount(1, "breakglass-a", "super_admin");
        emergencyAccount(2, "breakglass-b", "super_admin");
        double before = meters.counter("forcitis.platform.identity.emergency.check.failures").count();

        assertThat(run()).isEmpty();
        assertThat(recorded()).extracting(AuditEntry::action).doesNotContain("platform.identity.emergency-admin.check-failed");
        assertThat(meters.counter("forcitis.platform.identity.emergency.check.failures").count()).isEqualTo(before);
    }

    @Test
    @DisplayName("fewer than two usable accounts is an alert: audited, counted, reported")
    void fewerThanTwoAlerts() {
        emergencyAccount(1, "breakglass-a", "super_admin");
        double before = meters.counter("forcitis.platform.identity.emergency.check.failures").count();

        assertThat(run()).singleElement().asString().contains("only 1 usable");
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.emergency-admin.check-failed")).singleElement()
                .satisfies(entry -> assertThat(entry.detail()).contains("only 1 usable emergency account"));
        assertThat(meters.counter("forcitis.platform.identity.emergency.check.failures").count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("an account whose bound person no longer holds super_admin does not count and is named")
    void aBoundPersonWithoutSuperAdminAlerts() {
        emergencyAccount(1, "breakglass-a", "super_admin");
        emergencyAccount(2, "breakglass-b", "super_admin");
        emergencyAccount(3, "breakglass-c", "employee");

        assertThat(run()).singleElement().asString().contains("user 3").contains("no longer holds super_admin");
    }

    @Test
    @DisplayName("a disabled emergency account or a disabled bound account does not count")
    void disabledAccountsDoNotCount() {
        emergencyAccount(1, "breakglass-a", "super_admin");
        emergencyAccount(2, "breakglass-b", "super_admin");
        jdbc.sql("update platform_identity.emergency_admins set is_enabled = false where user_id = 1").update();
        assertThat(run()).singleElement().asString().contains("only 1 usable");

        jdbc.sql("update platform_identity.emergency_admins set is_enabled = true where user_id = 1").update();
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where user_id = 2").update();
        assertThat(run()).anySatisfy(problem -> assertThat(problem).contains("user 2")).anySatisfy(problem -> assertThat(problem).contains("only 1 usable"));
    }

    @Test
    @DisplayName("without any emergency account the check says so")
    void noAccountAtAll() {
        assertThat(run()).singleElement().asString().contains("only 0 usable");
    }
}
