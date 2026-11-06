package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.identity.domain.BrowserIdentifiers;
import com.forcitis.one.platform.identity.domain.Digests;
import com.forcitis.one.platform.identity.domain.IdentityCheckReason;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * C-M7: whether a login that arrives with a ticket from another site may skip the question "is this account yours". It may only when this
 * browser holds an identifier that was issued for this very account after the person confirmed it. Every use replaces the value, so a copy
 * of an old one is recognised as a replay. This protects against being logged in to someone else's account by a forged login request;
 * it does not protect against a stolen ticket or a stolen cookie.
 */
@Component
class BrowserIdentity {

    private static final int VALUE_BYTES = 32;
    private static final int HASH_PREFIX = 8;

    record Judgement(@Nullable IdentityCheckReason confirmationReason, @Nullable String newIdentifier) {

        boolean confirmationRequired() {
            return confirmationReason != null;
        }
    }

    private final BrowserIdentifiers identifiers;
    private final BrowserIdentifierSettings settings;
    private final AuditLog audit;
    private final ClientOrigin origin;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    BrowserIdentity(BrowserIdentifiers identifiers, BrowserIdentifierSettings settings, AuditLog audit, ClientOrigin origin, Clock clock) {
        this.identifiers = identifiers;
        this.settings = settings;
        this.audit = audit;
        this.origin = origin;
        this.clock = clock;
    }

    Judgement judge(UserId userId, LoginMethod method, @Nullable String presented) {
        Instant now = clock.instant();
        String presentedHash = presented == null || presented.isBlank() ? null : Digests.sha256Hex(presented);
        Optional<BrowserIdentifiers.Known> known = presentedHash == null ? Optional.empty() : identifiers.find(presentedHash, userId);
        IdentityCheckReason reason;
        if (presentedHash == null) {
            reason = IdentityCheckReason.NO_IDENTIFIER;
        } else if (known.isEmpty()) {
            reason = IdentityCheckReason.UNKNOWN_IDENTIFIER;
        } else if (known.get().retired()) {
            reason = IdentityCheckReason.REPLAYED_IDENTIFIER;
        } else if (known.get().lastLoginAt().plus(settings.lifetime()).isBefore(now)) {
            reason = IdentityCheckReason.EXPIRED_IDENTIFIER;
        } else {
            reason = null;
        }
        if (reason == IdentityCheckReason.REPLAYED_IDENTIFIER) {
            identifiers.invalidateAll(userId);
            audit.record(new AuditEntry("platform.identity.browser-identifier.replayed", userId, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                    "risk=HIGH identifier=" + prefix(presentedHash) + " every browser identifier of the account was invalidated")
                    .withTrace(origin.address(), AuditOutcome.FAILED, method.auditKind()));
        }
        String issued = null;
        if (reason == null) {
            issued = newValue();
            identifiers.replace(userId, presentedHash, Digests.sha256Hex(issued), now);
        }
        audit.record(new AuditEntry("platform.identity.login.identity-check", userId, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                reason == null ? "outcome=SKIPPED identifier=" + prefix(presentedHash)
                        : "outcome=CONFIRMATION_REQUIRED reason=" + reason + (presentedHash == null ? "" : " identifier=" + prefix(presentedHash)))
                .withTrace(origin.address(), reason == null ? AuditOutcome.SUCCEEDED : AuditOutcome.FAILED, method.auditKind()));
        return new Judgement(reason, issued);
    }

    String issue(UserId userId, @Nullable String presented) {
        String presentedHash = presented == null || presented.isBlank() ? null : Digests.sha256Hex(presented);
        boolean replaceable = presentedHash != null && identifiers.find(presentedHash, userId).map(known -> !known.retired()).orElse(true);
        String issued = newValue();
        identifiers.replace(userId, replaceable ? presentedHash : null, Digests.sha256Hex(issued), clock.instant());
        return issued;
    }

    void invalidateAll(UserId userId) {
        identifiers.invalidateAll(userId);
    }

    int purge() {
        return identifiers.purge(clock.instant().minus(settings.lifetime()));
    }

    private String newValue() {
        byte[] bytes = new byte[VALUE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String prefix(@Nullable String hash) {
        return hash == null ? "none" : hash.substring(0, HASH_PREFIX);
    }
}
