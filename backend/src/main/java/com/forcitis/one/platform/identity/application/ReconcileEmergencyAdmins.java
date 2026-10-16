package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily: at least two emergency accounts must be usable (enabled, bound person's account enabled and holding super_admin), and none
 * may be bound to a person who lost super_admin. Anything else is an alert to operations and the security auditors (ADR-0027 section 1).
 */
@Service
public class ReconcileEmergencyAdmins {

    static final int MINIMUM_USABLE = 2;

    private static final Logger log = LoggerFactory.getLogger(ReconcileEmergencyAdmins.class);

    private final EmergencyAdminRepository admins;
    private final AccountRepository accounts;
    private final AuditLog audit;
    private final MeterRegistry meters;

    ReconcileEmergencyAdmins(EmergencyAdminRepository admins, AccountRepository accounts, AuditLog audit, MeterRegistry meters) {
        this.admins = admins;
        this.accounts = accounts;
        this.audit = audit;
        this.meters = meters;
    }

    @Transactional
    public List<String> execute() {
        List<String> problems = new ArrayList<>();
        int usable = 0;
        for (EmergencyAdmin admin : admins.findAll()) {
            if (!admin.enabled()) {
                continue;
            }
            Optional<UserAccount> account = accounts.find(admin.userId());
            boolean accountEnabled = account.map(UserAccount::isEnabled).orElse(false);
            boolean holdsSuperAdmin = accountEnabled && AccessProfile.of(accounts.findEnabledRoles(admin.userId())).isAdministrator();
            if (holdsSuperAdmin) {
                usable++;
            } else {
                problems.add("the emergency account of user " + admin.userId().value() + " is bound to a person whose account is disabled or who no longer holds super_admin");
            }
        }
        if (usable < MINIMUM_USABLE) {
            problems.add("only " + usable + " usable emergency account(s), at least " + MINIMUM_USABLE + " are required");
        }
        if (!problems.isEmpty()) {
            String detail = String.join("; ", problems);
            audit.record(new AuditEntry("platform.identity.emergency-admin.check-failed", null, null, AuditSubject.without(AuditSubjectKind.OPERATION), detail));
            meters.counter("forcitis.platform.identity.emergency.check.failures").increment();
            log.error("ALERT platform.identity emergency account check failed: {}", detail);
        }
        return List.copyOf(problems);
    }
}
