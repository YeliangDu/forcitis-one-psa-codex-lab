package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ADR-0043 U-7(c): the client address lives in source_address only; the browser identification never reaches the audit entry. */
class LoginAuditDetailTest {
    static final String CLIENT_ADDRESS = "203.0.113.77";
    static final String BROWSER = "Mozilla/5.0 TestBrowser";
    static final UserId ACCOUNT = TestAccounts.account(6001);
    static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

    final ClientOrigin origin = mock(ClientOrigin.class);
    final AuditLog audit = mock(AuditLog.class);
    final SessionRegistry sessions = mock(SessionRegistry.class);
    LoginAdmission admission;

    @BeforeEach
    void admit() {
        when(origin.address()).thenReturn(CLIENT_ADDRESS);
        when(origin.userAgent()).thenReturn(BROWSER);
        when(sessions.start(any(), any(), anyBoolean())).thenReturn(new StartedSession(NOW.plusSeconds(3600), null));
        admission = new LoginAdmission(mock(AccountProvisioning.class), mock(AccountRepository.class), audit, sessions, origin,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry(), mock(BrowserIdentity.class));
    }

    private void assertDetailWithoutOrigin(AuditEntry entry, String expected) {
        assertThat(entry.detail()).isEqualTo(expected).doesNotContain("address=", "agent=", CLIENT_ADDRESS, BROWSER);
        assertThat(entry.sourceAddress()).isEqualTo(CLIENT_ADDRESS);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: a successful login's detail carries the method only, the address stays in source_address")
    void successDetailOmitsAddressAndAgent() {
        admission.complete(ACCOUNT, LoginMethod.SIGNED_TICKET);

        ArgumentCaptor<AuditEntry> recorded = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(recorded.capture());
        assertDetailWithoutOrigin(recorded.getValue(), "method=SIGNED_TICKET");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: a failed login's detail carries method and reason only, the address stays in source_address")
    void failureDetailOmitsAddressAndAgent() {
        admission.reject(IdentityError.PLATFORM_IDENTITY_ACCOUNT_DISABLED, LoginMethod.SIGNED_TICKET, ACCOUNT, "account disabled");

        ArgumentCaptor<AuditEntry> recorded = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).recordIndependently(recorded.capture());
        assertDetailWithoutOrigin(recorded.getValue(), "method=SIGNED_TICKET reason=account disabled");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: a login refused by network has no actor and still keeps address and agent out of detail")
    void deniedDetailOmitsAddressAndAgent() {
        admission.reject(IdentityError.PLATFORM_IDENTITY_NETWORK_DENIED, LoginMethod.EMERGENCY, null, "outside the internal network");

        ArgumentCaptor<AuditEntry> recorded = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).recordIndependently(recorded.capture());
        assertDetailWithoutOrigin(recorded.getValue(), "method=EMERGENCY reason=outside the internal network");
    }
}
