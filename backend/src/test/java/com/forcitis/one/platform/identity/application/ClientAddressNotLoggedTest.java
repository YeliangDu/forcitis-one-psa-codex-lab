package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.integration.api.WecomEntry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

/** GUIDING_PRINCIPLES section 11 O-3: the application log never carries a client address; the audit source_address column does. */
class ClientAddressNotLoggedTest {
    static final String CLIENT_ADDRESS = "203.0.113.77";
    static final UserId ACCOUNT = TestAccounts.account(6001);
    static final EmployeeId EMPLOYEE = TestAccounts.employee(1001);
    static final Instant UNTIL = Instant.parse("2026-10-14T03:00:00Z");

    final ClientOrigin origin = mock(ClientOrigin.class);
    final AuditLog audit = mock(AuditLog.class);
    final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void capture() {
        when(origin.address()).thenReturn(CLIENT_ADDRESS);
        logged.start();
        ((Logger) LoggerFactory.getLogger(LoginAdmission.class)).addAppender(logged);
        ((Logger) LoggerFactory.getLogger(EnterAdministratorMode.class)).addAppender(logged);
    }

    @AfterEach
    void release() {
        ((Logger) LoggerFactory.getLogger(LoginAdmission.class)).detachAppender(logged);
        ((Logger) LoggerFactory.getLogger(EnterAdministratorMode.class)).detachAppender(logged);
        logged.stop();
    }

    private void assertAlertWithoutAddress() {
        assertThat(logged.list).isNotEmpty();
        assertThat(logged.list).anySatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("ALERT platform.identity").contains(ACCOUNT.value().toString());
        });
        assertThat(logged.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(CLIENT_ADDRESS);
            assertThat(event.getMessage()).doesNotContain(CLIENT_ADDRESS);
            assertThat(event.getArgumentArray() == null ? List.of() : List.of(event.getArgumentArray()))
                    .noneMatch(argument -> String.valueOf(argument).contains(CLIENT_ADDRESS));
        });
    }

    private void assertAuditKeepsAddress(String action) {
        ArgumentCaptor<AuditEntry> recorded = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit, atLeastOnce()).record(recorded.capture());
        assertThat(recorded.getAllValues()).filteredOn(entry -> entry.action().equals(action)).isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.sourceAddress()).isEqualTo(CLIENT_ADDRESS));
    }

    @Test
    @DisplayName("O-3: the emergency login alert is logged without the client address, while the audit entry keeps it")
    void emergencyLoginAlertOmitsClientAddress() {
        SessionRegistry sessions = mock(SessionRegistry.class);
        when(sessions.start(any(), any(), anyBoolean()))
                .thenReturn(new StartedSession(UNTIL.plusSeconds(3600), UNTIL));
        LoginAdmission admission = new LoginAdmission(mock(AccountProvisioning.class), mock(AccountRepository.class), audit, sessions, origin,
                Clock.fixed(UNTIL, ZoneOffset.UTC), new SimpleMeterRegistry(), mock(BrowserIdentity.class));

        admission.complete(ACCOUNT, LoginMethod.EMERGENCY);

        assertAuditKeepsAddress("platform.identity.emergency.login");
        assertAlertWithoutAddress();
    }

    @Test
    @DisplayName("O-3: entering the administrator mode is logged without the client address, while the audit entry keeps it")
    void administratorModeEntryOmitsClientAddress() {
        CurrentUser user = mock(CurrentUser.class);
        SessionContext session = mock(SessionContext.class);
        SessionRegistry sessions = mock(SessionRegistry.class);
        WecomIdentification identification = mock(WecomIdentification.class);
        AccountRepository accounts = mock(AccountRepository.class);
        when(user.userId()).thenReturn(ACCOUNT);
        when(user.employeeId()).thenReturn(EMPLOYEE);
        when(session.mode()).thenReturn(SessionMode.ORDINARY);
        when(session.loginMethod()).thenReturn(LoginMethod.values()[0]);
        when(sessions.beginAdministratorMode()).thenReturn(UNTIL);
        when(identification.identify(any(), any())).thenReturn(new WecomIdentification.Outcome.Identified(
                new EmployeeProfile(EMPLOYEE, "person", TestOrgIds.orgUnit(5), null, EmploymentStatus.EMPLOYED, false)));
        Role superAdmin = mock(Role.class);
        when(superAdmin.isEnabled()).thenReturn(true);
        when(superAdmin.code()).thenReturn(Role.SUPER_ADMIN);
        when(superAdmin.permissionCodes()).thenReturn(Set.of());
        when(accounts.findEnabledRoles(ACCOUNT)).thenReturn(List.of(superAdmin));
        EnterAdministratorMode enter = new EnterAdministratorMode(user, session, sessions, identification, accounts, audit, origin,
                new SimpleMeterRegistry(), mock(PlatformTransactionManager.class));

        enter.execute(new WecomLoginCommand("code", "state", "binding", WecomEntry.values()[0]));

        assertAuditKeepsAddress("platform.identity.administrator-mode.entered");
        assertAlertWithoutAddress();
    }

    @Test
    @DisplayName("O-3: when the failed-login audit entry cannot be written, the log line carries the failure type but not the client address from its message")
    void failedAuditWriteOmitsClientAddress() {
        doThrow(new IllegalStateException("Failing row contains (" + CLIENT_ADDRESS + ")")).when(audit).recordIndependently(any());
        LoginAdmission admission = new LoginAdmission(mock(AccountProvisioning.class), mock(AccountRepository.class), audit, mock(SessionRegistry.class),
                origin, Clock.fixed(UNTIL, ZoneOffset.UTC), new SimpleMeterRegistry(), mock(BrowserIdentity.class));

        admission.reject(IdentityError.PLATFORM_IDENTITY_ACCOUNT_DISABLED, LoginMethod.EMERGENCY, ACCOUNT, "reason");

        assertThat(logged.list).isNotEmpty();
        assertThat(logged.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("IllegalStateException"));
        assertThat(logged.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(CLIENT_ADDRESS));
    }
}
