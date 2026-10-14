package com.forcitis.one.platform.identity.application;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.NetworkAllowList;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Break-glass login (C1a-04, RULE-316/317): only from the internal network, Argon2id password plus TOTP, 5 failures lock the
 * account for 15 minutes. Unknown login name, wrong password, wrong code, locked and disabled all look the same to the
 * caller and cost about the same time (RULE-316). The caller's address is the one resolved through the trusted proxies.
 */
@Service
public class LoginAsEmergencyAdmin {

    private static final int TOTP_WINDOW_STEPS = 1;

    private final EmergencyAdminRepository admins;
    private final PasswordHasher hasher;
    private final TotpSecretVault vault;
    private final OrgDirectory directory;
    private final ClientOrigin origin;
    private final NetworkAllowList internalNetworks;
    private final EmergencySettings settings;
    private final LoginAdmission admission;
    private final Clock clock;

    LoginAsEmergencyAdmin(EmergencyAdminRepository admins, PasswordHasher hasher, TotpSecretVault vault, OrgDirectory directory,
            ClientOrigin origin, EmergencySettings settings, LoginAdmission admission, Clock clock) {
        this.admins = admins;
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
        if (!admin.enabled() || admin.isLockedAt(now)) {
            hasher.spendVerificationTime(password);
            return rejectCredentials(admin, admin.enabled() ? "locked until " + admin.lockedUntil() : "disabled");
        }
        if (!hasher.matches(password, admin.passwordHash())) {
            return failed(admin, now, "wrong password");
        }
        OptionalLong step = verifiedStep(admin, command.totpCode(), now);
        if (step.isEmpty()) {
            return failed(admin, now, "wrong one-time code");
        }
        if (!admins.acceptTotpStep(admin.userId(), step.getAsLong())) {
            return failed(admin, now, "one-time code already used");
        }
        admins.recordSuccess(admin.userId());
        Optional<EmployeeProfile> employee = directory.findEmployee(admin.userId());
        if (employee.isEmpty()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED, method, admin.userId(), "no employee record");
        }
        Optional<IdentityError> refused = admission.check(employee.get());
        if (refused.isPresent()) {
            return admission.reject(refused.get(), method, admin.userId(), refused.get().name());
        }
        return admission.complete(admin.userId(), method);
    }

    private OptionalLong verifiedStep(EmergencyAdmin admin, String code, Instant now) {
        try {
            return Totp.verify(vault.open(admin.totpSecretEncrypted()), code, now, TOTP_WINDOW_STEPS);
        } catch (IllegalStateException vaultUnavailable) {
            // no encryption key configured, or the stored secret cannot be opened: fail closed, same answer as a wrong code
            return OptionalLong.empty();
        }
    }

    private LoginResult failed(EmergencyAdmin admin, Instant now, String reason) {
        admins.recordFailure(admin.userId(), now, settings.maxAttempts(), settings.lockDuration());
        return rejectCredentials(admin, reason);
    }

    private LoginResult rejectCredentials(EmergencyAdmin admin, String reason) {
        return admission.reject(IdentityError.PLATFORM_IDENTITY_CREDENTIALS_INVALID, LoginMethod.EMERGENCY,
                admin == null ? null : admin.userId(), reason);
    }
}
