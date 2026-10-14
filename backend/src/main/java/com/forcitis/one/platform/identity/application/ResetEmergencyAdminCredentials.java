package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** New password and new one-time-code secret; the old ones stop working and the person's other sessions end (D-1a-47). */
@Service
public class ResetEmergencyAdminCredentials {

    private final EmergencyAdminRepository admins;
    private final EmergencyBinding binding;
    private final EmergencyCredentialsFactory credentials;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final DomainEvents events;
    private final AuditLog audit;

    ResetEmergencyAdminCredentials(EmergencyAdminRepository admins, EmergencyBinding binding, EmergencyCredentialsFactory credentials,
            SessionRegistry sessions, AccessCache cache, DomainEvents events, AuditLog audit) {
        this.admins = admins;
        this.binding = binding;
        this.credentials = credentials;
        this.sessions = sessions;
        this.cache = cache;
        this.events = events;
        this.audit = audit;
    }

    @Transactional
    public IssuedEmergencyCredentials execute(OperationAttestation attestation, long personId) {
        UserId person = new UserId(personId);
        EmergencyAdmin admin = binding.require(person);
        binding.requireValid(person);
        EmergencyCredentialsFactory.Generated generated = credentials.generate(admin.loginName());
        admin.resetCredentials(generated.passwordHash(), generated.sealedTotpSecret());
        admins.save(admin);
        events.publish(admin);
        sessions.revokeAll(person);
        cache.evict(person);
        audit.record(new AuditEntry("platform.identity.operations.emergency-admin.credentials-reset", null, null, "emergency-admin:" + personId,
                attestation.describe()));
        return generated.shown();
    }
}
