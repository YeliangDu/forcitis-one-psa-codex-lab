package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The emergency account bound to a separated person is disabled (ADR-0027 section 1). It is its own aggregate and so has its own
 * listener and transaction, apart from the account and its grants in HandleEmployeeSeparated.
 */
@SystemActorName("platform.identity.separation")
@Component
class DisableEmergencyAdminOnSeparation {

    private static final Logger log = LoggerFactory.getLogger(DisableEmergencyAdminOnSeparation.class);

    private final EmergencyAdminRepository emergencyAdmins;
    private final DomainEvents events;
    private final AccountEmployees accountEmployees;

    DisableEmergencyAdminOnSeparation(EmergencyAdminRepository emergencyAdmins, DomainEvents events, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.emergencyAdmins = emergencyAdmins;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(EmployeeSeparated event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null),
                "platform.identity.separation")) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(this::disable);
        }
    }

    private void disable(UserId userId) {
        Optional<EmergencyAdmin> found = emergencyAdmins.findByUserId(userId);
        if (found.isEmpty() || !found.get().enabled()) {
            return;
        }
        EmergencyAdmin admin = found.get();
        admin.disable(EmergencyAdminDisableCause.SEPARATION);
        emergencyAdmins.save(admin);
        events.publish(admin);
        log.error("ALERT platform.identity the emergency account bound to separated user {} was disabled; check that at least two usable ones remain",
                userId.value());
    }
}
