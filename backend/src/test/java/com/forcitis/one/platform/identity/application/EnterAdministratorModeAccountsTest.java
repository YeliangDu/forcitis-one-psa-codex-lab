package com.forcitis.one.platform.identity.application;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.integration.api.WecomEntry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import com.forcitis.one.fixture.doubles.TestOrgIds;

/**
 * Entering the administrator mode needs a fresh WeCom code that proves the signed-in person. The code proves an employee, the session
 * belongs to an account: the signed-in account is 6001 and its employee 1001, and the employee numbered like the account (6001) is
 * somebody else, whose code must not open the mode.
 */
class EnterAdministratorModeAccountsTest {
    static final UserId ACCOUNT = TestAccounts.account(6001);
    static final EmployeeId SIGNED_IN_EMPLOYEE = TestAccounts.employee(1001);
    static final Instant UNTIL = Instant.parse("2026-10-14T03:00:00Z");

    final CurrentUser currentUser = mock(CurrentUser.class);
    final SessionContext session = mock(SessionContext.class);
    final SessionRegistry sessions = mock(SessionRegistry.class);
    final WecomIdentification identification = mock(WecomIdentification.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final AuditLog audit = mock(AuditLog.class);
    final ClientOrigin origin = mock(ClientOrigin.class);
    final EnterAdministratorMode enter = new EnterAdministratorMode(currentUser, session, sessions, identification, accounts, audit, origin,
            new SimpleMeterRegistry(), mock(PlatformTransactionManager.class));
    final WecomLoginCommand command = new WecomLoginCommand("code", "state", "binding", WecomEntry.values()[0]);

    EnterAdministratorModeAccountsTest() {
        when(currentUser.userId()).thenReturn(ACCOUNT);
        when(currentUser.employeeId()).thenReturn(SIGNED_IN_EMPLOYEE);
        when(session.mode()).thenReturn(SessionMode.ORDINARY);
        when(session.loginMethod()).thenReturn(LoginMethod.values()[0]);
        when(origin.address()).thenReturn("127.0.0.1");
        when(sessions.beginAdministratorMode()).thenReturn(UNTIL);
        Role superAdmin = mock(Role.class);
        when(superAdmin.isEnabled()).thenReturn(true);
        when(superAdmin.code()).thenReturn(Role.SUPER_ADMIN);
        when(superAdmin.permissionCodes()).thenReturn(Set.of());
        when(accounts.findEnabledRoles(ACCOUNT)).thenReturn(List.of(superAdmin));
    }

    private void proves(EmployeeId employee) {
        when(identification.identify(any(), any())).thenReturn(new WecomIdentification.Outcome.Identified(
                new EmployeeProfile(employee, "person", TestOrgIds.orgUnit(5), null, EmploymentStatus.EMPLOYED, false)));
    }

    @Test
    @DisplayName("a code that proves the signed-in account's employee opens the mode")
    void codeOfTheSignedInEmployeeOpensTheMode() {
        proves(SIGNED_IN_EMPLOYEE);

        assertThat(enter.execute(command)).isEqualTo(UNTIL);
    }

    @Test
    @DisplayName("a code that proves the employee numbered like the account is another person: refused, audited, and the mode stays shut")
    void codeOfTheEmployeeNumberedLikeTheAccountIsRefused() {
        proves(TestAccounts.employee(6001));

        assertThatThrownBy(() -> enter.execute(command)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(IdentityError.PLATFORM_IDENTITY_ADMINISTRATOR_MODE_DENIED));
        verify(audit).recordIndependently(any());
        verify(sessions, never()).beginAdministratorMode();
    }
}
