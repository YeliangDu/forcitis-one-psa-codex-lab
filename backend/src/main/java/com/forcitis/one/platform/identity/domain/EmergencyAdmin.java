package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.events.EmergencyAdminCredentialsReset;
import com.forcitis.one.platform.identity.events.EmergencyAdminDisabled;
import com.forcitis.one.platform.identity.events.EmergencyAdminEnabled;
import com.forcitis.one.platform.identity.events.EmergencyAdminRegistered;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A break-glass login bound to the person who holds super_admin (ADR-0027 section 1). The failure counter, the lock and the
 * accepted one-time-code step are access metadata kept by conditional atomic updates; they are not part of this state.
 */
public final class EmergencyAdmin extends AggregateRoot {

    public static final StateMachine<EmergencyAdminStatus> MACHINE = StateMachine.of(EmergencyAdminStatus.class)
            .allow(EmergencyAdminStatus.ENABLED, EmergencyAdminStatus.DISABLED)
            .allow(EmergencyAdminStatus.DISABLED, EmergencyAdminStatus.ENABLED)
            .build();

    private final UserId userId;
    private final String loginName;
    private final EmergencyAdminStatus loadedStatus;
    private final boolean stored;
    private final long lastTotpStep;
    private final int failedAttempts;
    private final @Nullable Instant lockedUntil;
    private String passwordHash;
    private String totpSecretEncrypted;
    private EmergencyAdminStatus status;
    private boolean credentialsChanged;

    private EmergencyAdmin(UserId userId, String loginName, String passwordHash, String totpSecretEncrypted, long lastTotpStep,
                           int failedAttempts, @Nullable Instant lockedUntil, EmergencyAdminStatus status, boolean stored, long version,
                           long eventSequence) {
        super(version, eventSequence);
        this.userId = Objects.requireNonNull(userId, "userId");
        this.loginName = Objects.requireNonNull(loginName, "loginName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.totpSecretEncrypted = Objects.requireNonNull(totpSecretEncrypted, "totpSecretEncrypted");
        this.lastTotpStep = lastTotpStep;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
        this.stored = stored;
    }

    public static EmergencyAdmin register(UserId userId, String loginName, String passwordHash, String totpSecretEncrypted) {
        EmergencyAdmin admin = new EmergencyAdmin(userId, loginName, passwordHash, totpSecretEncrypted, 0, 0, null,
                EmergencyAdminStatus.ENABLED, false, 0, 0);
        admin.record((envelopes, version) -> new EmergencyAdminRegistered(envelopes.wrap(admin.draft("EmergencyAdminRegistered", version)),
                userId));
        return admin;
    }

    public static EmergencyAdmin restore(UserId userId, String loginName, String passwordHash, String totpSecretEncrypted,
                                         long lastTotpStep, int failedAttempts, @Nullable Instant lockedUntil, boolean enabled, long version,
                                         long eventSequence) {
        return new EmergencyAdmin(userId, loginName, passwordHash, totpSecretEncrypted, lastTotpStep, failedAttempts, lockedUntil,
                enabled ? EmergencyAdminStatus.ENABLED : EmergencyAdminStatus.DISABLED, true, version, eventSequence);
    }

    public UserId userId() {
        return userId;
    }

    public String loginName() {
        return loginName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String totpSecretEncrypted() {
        return totpSecretEncrypted;
    }

    public long lastTotpStep() {
        return lastTotpStep;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public @Nullable Instant lockedUntil() {
        return lockedUntil;
    }

    public EmergencyAdminStatus status() {
        return status;
    }

    public EmergencyAdminStatus loadedStatus() {
        return loadedStatus;
    }

    public boolean enabled() {
        return status == EmergencyAdminStatus.ENABLED;
    }

    public boolean isStored() {
        return stored;
    }

    public boolean credentialsChanged() {
        return credentialsChanged;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void disable(EmergencyAdminDisableCause cause) {
        Objects.requireNonNull(cause, "cause");
        this.status = MACHINE.require(status, EmergencyAdminStatus.DISABLED, IdentityError.PLATFORM_IDENTITY_EMERGENCY_ADMIN_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new EmergencyAdminDisabled(envelopes.wrap(draft("EmergencyAdminDisabled", version)), userId, cause));
    }

    public void enable() {
        this.status = MACHINE.require(status, EmergencyAdminStatus.ENABLED, IdentityError.PLATFORM_IDENTITY_EMERGENCY_ADMIN_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new EmergencyAdminEnabled(envelopes.wrap(draft("EmergencyAdminEnabled", version)), userId));
    }

    public void resetCredentials(String newPasswordHash, String newTotpSecretEncrypted) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.totpSecretEncrypted = Objects.requireNonNull(newTotpSecretEncrypted, "newTotpSecretEncrypted");
        this.credentialsChanged = true;
        record((envelopes, version) -> new EmergencyAdminCredentialsReset(envelopes.wrap(draft("EmergencyAdminCredentialsReset", version)),
                userId));
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.identity." + eventName, 1, "EmergencyAdmin", userId.value(), version, null, null, List.of());
    }
}
