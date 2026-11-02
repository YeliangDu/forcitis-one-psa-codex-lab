package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.NetworkAllowList;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unknown login name, wrong password, wrong code, locked and disabled all look the same and cost about the same time. After the
 * credentials the bound account must be enabled and hold super_admin now. A current organisation snapshot must list the bound
 * employee as employed; a snapshot that is missing or stale while the source is down lets the login through with an alert, but a person
 * it lists as separated is still refused (ADR-0027 section 1; ADR-0033 section 3 item 7).
 */
@Service
public class LoginAsEmergencyAdmin {

    private static final Logger log = LoggerFactory.getLogger(LoginAsEmergencyAdmin.class);
    private static final int TOTP_WINDOW_STEPS = 1;

    private final EmergencyAdminRepository admins;
    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    private final TotpSecretVault vault;
    private final OrgDirectory directory;
    private final ClientOrigin origin;
    private final NetworkAllowList internalNetworks;
    private final EmergencySettings settings;
    private final LoginAdmission admission;
    private final Clock clock;

    LoginAsEmergencyAdmin(EmergencyAdminRepository admins, AccountRepository accounts, PasswordHasher hasher, TotpSecretVault vault,
            OrgDirectory directory, ClientOrigin origin, EmergencySettings settings, LoginAdmission admission, Clock clock) {
        this.admins = admins;
        this.accounts = accounts;
        this.hasher = hasher;
        this.vault = vault;
        this.directory = directory;
        this.origin = origin;
        this.internalNetworks = NetworkAllowList.of(settings.internalNetworks());
        this.settings = settings;
        this.admission = admission;
        this.clock = clock;
    }

    @Transactional
    public LoginResult execute(EmergencyLoginCommand command) {
        LoginMethod method = LoginMethod.EMERGENCY;
        if (!internalNetworks.allows(origin.address())) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_NETWORK_DENIED, method, null, "outside the internal network");
        }
        String password = command.password() == null ? "" : command.password();
        Optional<EmergencyAdmin> found = command.loginName() == null ? Optional.empty() : admins.findByLoginName(command.loginName());
        if (found.isEmpty()) {
            hasher.spendVerificationTime(password);
            return rejectCredentials(null, "unknown login name");
        }
        EmergencyAdmin admin = found.get();
        Instant now = clock.instant();
        if (!admin.enabled() || !admins.reserveAttempt(admin.userId(), now, settings.maxAttempts(), settings.lockDuration())) {
            hasher.spendVerificationTime(password);
            return rejectCredentials(admin, admin.enabled() ? "locked" : "disabled");
        }
        if (!hasher.matches(password, admin.passwordHash())) {
            return rejectCredentials(admin, "wrong password");
        }
        OptionalLong step = verifiedStep(admin, command.totpCode(), now);
        if (step.isEmpty()) {
            return rejectCredentials(admin, "wrong one-time code");
        }
        if (!admins.acceptTotpStep(admin.userId(), step.getAsLong())) {
            return rejectCredentials(admin, "one-time code already used");
        }
        admins.recordSuccess(admin.userId());
        Optional<UserAccount> account = accounts.find(admin.userId());
        if (account.isEmpty() || !account.get().isEnabled()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_ACCOUNT_DISABLED, method, admin.userId(), "bound account missing or disabled");
        }
        if (!AccessProfile.of(accounts.findEnabledRoles(admin.userId())).isAdministrator()) {
            return rejectCredentials(admin, "the bound person no longer holds super_admin");
        }
        EmployeeId bound = account.get().employeeId();
        switch (directory.employmentOf(bound)) {
            case SEPARATED -> {
                return admission.reject(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED, method, admin.userId(),
                        "the bound person is separated");
            }
            case NOT_LISTED -> {
                log.error("ALERT platform.identity emergency login of user {} refused: the directory does not list employee {}",
                        admin.userId().value(), bound.value());
                return admission.reject(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED, method, admin.userId(),
                        "the directory does not list the bound person");
            }
            case EMPLOYED -> {
            }
        }
        return admission.complete(admin.userId(), method);
    }

    private OptionalLong verifiedStep(EmergencyAdmin admin, String code, Instant now) {
        try {
            return Totp.verify(vault.open(admin.totpSecretEncrypted()), code, now, TOTP_WINDOW_STEPS);
        } catch (IllegalStateException vaultUnavailable) {
            // fail closed: no key or an unreadable secret gives the same answer as a wrong code
            return OptionalLong.empty();
        }
    }

    private LoginResult rejectCredentials(EmergencyAdmin admin, String reason) {
        return admission.reject(IdentityError.PLATFORM_IDENTITY_CREDENTIALS_INVALID, LoginMethod.EMERGENCY,
                admin == null ? null : admin.userId(), reason);
    }
}
