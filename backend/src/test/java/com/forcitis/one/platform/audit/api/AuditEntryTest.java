package com.forcitis.one.platform.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditEntryTest {

    private final AuditEntry entry = new AuditEntry("platform.identity.login.succeeded", null, null,
            AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, UUID.randomUUID()), "x");

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-08: a login method without an outcome is refused before it reaches the database")
    void loginMethodWithoutOutcomeIsRefused() {
        assertThatThrownBy(() -> entry.withTrace("10.0.0.5", null, "SSO")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ck_audit_entries_login_method_outcome");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-08: outcome without a method, and no trace at all, are accepted")
    void otherCombinationsAreAccepted() {
        assertThat(entry.withTrace("10.0.0.5", AuditOutcome.FAILED, null).outcome()).isEqualTo(AuditOutcome.FAILED);
        assertThat(entry.withTrace(null, AuditOutcome.SUCCEEDED, "SSO").loginMethod()).isEqualTo("SSO");
        assertThat(entry.withTrace(null, null, null).outcome()).isNull();
    }
}
