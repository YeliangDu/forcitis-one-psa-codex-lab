package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operations command: only for a person who still holds super_admin. Enabling an enabled account changes nothing. */
@Service
public class EnableEmergencyAdmin {

    private final EmergencyAdminRepository admins;
    private final EmergencyBinding binding;
    private final DomainEvents events;
    private final AuditLog audit;

    EnableEmergencyAdmin(EmergencyAdminRepository admins, EmergencyBinding binding, DomainEvents events, AuditLog audit) {
        this.admins = admins;
        this.binding = binding;
        this.events = events;
        this.audit = audit;
    }

    /** True when the account was disabled and now is enabled. */
    @Transactional
    public boolean execute(OperationAttestation attestation, EmployeeId employeeId) {
        UserId person = binding.boundAccount(employeeId);
        EmergencyAdmin admin = binding.require(person);
        if (admin.enabled()) {
            return false;
        }
        binding.requireValid(person);
        admin.enable();
        admins.save(admin);
        events.publish(admin);
        audit.record(new AuditEntry("platform.identity.operations.emergency-admin.enabled", null, null, "emergency-admin:" + person.value(),
                attestation.describe()));
        return true;
    }
}
