package com.forcitis.one.platform.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The break-glass login is refused when the organisation snapshot lists the bound person as separated (ADR-0033 section 3, first gate).
 * The bound account is 6001 and its employee 1001; the employee numbered like the account (6001) is a different person. A check that
 * looked the account number up as an employee would read that other person and let a separated administrator in.
 */
class LoginAsEmergencyAdminAccountsTest {
    static final UserId ACCOUNT = new UserId(6001);
    static final EmployeeId BOUND_EMPLOYEE = new EmployeeId(1001);
    static final EmployeeId EMPLOYEE_NUMBERED_LIKE_THE_ACCOUNT = new EmployeeId(6001);
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    static final byte[] SECRET = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    final EmergencyAdminRepository admins = mock(EmergencyAdminRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final PasswordHasher hasher = mock(PasswordHasher.class);
    final TotpSecretVault vault = mock(TotpSecretVault.class);
    final OrgDirectory directory = mock(OrgDirectory.class);
    final ClientOrigin origin = mock(ClientOrigin.class);
    final LoginAdmission admission = mock(LoginAdmission.class);
    final LoginAsEmergencyAdmin login = new LoginAsEmergencyAdmin(admins, accounts, hasher, vault, directory, origin,
            new EmergencySettings(List.of("127.0.0.1/32"), 5, Duration.ofMinutes(15), ""), admission, TestAccounts.apart(5000),
            Clock.fixed(NOW, ZoneOffset.UTC));

    LoginAsEmergencyAdminAccountsTest() {
        EmergencyAdmin admin = EmergencyAdmin.register(ACCOUNT, "breakglass", "hash", "sealed");
        when(origin.address()).thenReturn("127.0.0.1");
        when(admins.findByLoginName("breakglass")).thenReturn(Optional.of(admin));
        when(admins.reserveAttempt(eq(ACCOUNT), any(), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(true);
        when(admins.acceptTotpStep(eq(ACCOUNT), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        when(hasher.matches("secret-password", "hash")).thenReturn(true);
        when(vault.open("sealed")).thenReturn(SECRET);
        UserAccount account = mock(UserAccount.class);
        when(account.isEnabled()).thenReturn(true);
        when(accounts.find(ACCOUNT)).thenReturn(Optional.of(account));
        Role superAdmin = mock(Role.class);
        when(superAdmin.isEnabled()).thenReturn(true);
        when(superAdmin.code()).thenReturn(Role.SUPER_ADMIN);
        when(superAdmin.permissionCodes()).thenReturn(Set.of());
        when(accounts.findEnabledRoles(ACCOUNT)).thenReturn(List.of(superAdmin));
    }

    private EmergencyLoginCommand command() {
        return new EmergencyLoginCommand("breakglass", "secret-password", Totp.code(SECRET, Totp.stepAt(NOW)));
    }

    private void listed(EmployeeId employee, EmploymentStatus status) {
        when(directory.findEmployee(employee)).thenReturn(Optional.of(new EmployeeProfile(employee, "person", new OrgUnitId(5), null, status, false)));
    }

    @Test
    @DisplayName("a bound employee that the snapshot lists as separated is refused, whoever is numbered like the account")
    void separatedBoundEmployeeRefusedWhenNumbersDiffer() {
        listed(BOUND_EMPLOYEE, EmploymentStatus.SEPARATED);
        listed(EMPLOYEE_NUMBERED_LIKE_THE_ACCOUNT, EmploymentStatus.EMPLOYED);

        login.execute(command());

        verify(admission).reject(eq(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED), any(), eq(ACCOUNT), any());
        verify(admission, never()).complete(any(), any());
    }

    @Test
    @DisplayName("a bound employee who is employed gets in, even when the employee numbered like the account is listed as separated")
    void employedBoundEmployeeAdmittedWhenNumbersDiffer() {
        listed(BOUND_EMPLOYEE, EmploymentStatus.EMPLOYED);
        listed(EMPLOYEE_NUMBERED_LIKE_THE_ACCOUNT, EmploymentStatus.SEPARATED);

        login.execute(command());

        verify(admission).complete(eq(ACCOUNT), any());
        verify(admission, never()).reject(eq(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED), any(), any(), any());
    }
}
