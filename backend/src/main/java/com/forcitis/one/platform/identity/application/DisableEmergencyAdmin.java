package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operations command: the second line after the automatic disabling on separation. Disabling a disabled account changes nothing. */
@Service
public class DisableEmergencyAdmin {

    private final EmergencyAdminRepository admins;
    private final EmergencyBinding binding;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final DomainEvents events;
    private final AuditLog audit;

    DisableEmergencyAdmin(EmergencyAdminRepository admins, EmergencyBinding binding, SessionRegistry sessions, AccessCache cache,
            DomainEvents events, AuditLog audit) {
        this.admins = admins;
        this.binding = binding;
        this.sessions = sessions;
        this.cache = cache;
        this.events = events;
        this.audit = audit;
    }

    /** True when the account was enabled and now is not. */
    @Transactional
    public boolean execute(OperationAttestation attestation, long personId) {
        UserId person = new UserId(personId);
        EmergencyAdmin admin = binding.require(person);
        if (!admin.enabled()) {
            return false;
        }
        admin.disable(EmergencyAdminDisableCause.OPERATIONS);
        admins.save(admin);
        events.publish(admin);
        sessions.revokeAll(person);
        cache.evict(person);
        audit.record(new AuditEntry("platform.identity.operations.emergency-admin.disabled", null, null, "emergency-admin:" + personId,
                attestation.describe()));
        return true;
    }
}
