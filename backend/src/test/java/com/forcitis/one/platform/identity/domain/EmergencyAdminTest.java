package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause;
import com.forcitis.one.platform.identity.events.EmergencyAdminCredentialsReset;
import com.forcitis.one.platform.identity.events.EmergencyAdminDisabled;
import com.forcitis.one.platform.identity.events.EmergencyAdminEnabled;
import com.forcitis.one.platform.identity.events.EmergencyAdminRegistered;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmergencyAdminTest {

    static EmergencyAdmin at(EmergencyAdminStatus status) {
        return EmergencyAdmin.restore(TestAccounts.account(7), "breakglass", "hash", "sealed", 0, 0, null, status == EmergencyAdminStatus.ENABLED, 3, 3);
    }

    @Test
    @DisplayName("ADR-0027 1: enable and disable are tried from every status; only the opposite status is legal")
    void transitions() {
        new TransitionTable<EmergencyAdminStatus, EmergencyAdmin>(EmergencyAdminStatus.class, EmergencyAdminTest::at, EmergencyAdmin::status)
                .operation("enable", EmergencyAdmin::enable, EmergencyAdminStatus.DISABLED, EmergencyAdminStatus.ENABLED)
                .operation("disable", a -> a.disable(EmergencyAdminDisableCause.OPERATIONS), EmergencyAdminStatus.ENABLED, EmergencyAdminStatus.DISABLED)
                .verify();
    }

    @Test
    @DisplayName("registering, disabling, enabling and resetting each record their event, numbered without gaps")
    void events() {
        EmergencyAdmin admin = EmergencyAdmin.register(TestAccounts.account(7), "breakglass", "hash", "sealed");
        admin.disable(EmergencyAdminDisableCause.SEPARATION);
        admin.enable();
        admin.resetCredentials("hash2", "sealed2");

        assertThat(admin.credentialsChanged()).isTrue();
        assertThat(admin.drainEvents(TestEnvelopes.INSTANCE)).satisfiesExactly(
                e -> assertThat(e).isInstanceOf(EmergencyAdminRegistered.class),
                e -> assertThat(e).isInstanceOfSatisfying(EmergencyAdminDisabled.class, d -> assertThat(d.cause()).isEqualTo(EmergencyAdminDisableCause.SEPARATION)),
                e -> assertThat(e).isInstanceOf(EmergencyAdminEnabled.class),
                e -> assertThat(e).isInstanceOf(EmergencyAdminCredentialsReset.class));
        assertThat(admin.eventSequence()).isEqualTo(4);
    }
}
