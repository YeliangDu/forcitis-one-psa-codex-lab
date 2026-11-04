package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.AccountOpeningRoute;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations command, run with a witness and never through the web. Registering the same person again with the same login name
 * changes nothing and issues nothing; to get new credentials use the reset command.
 */
@Service
public class RegisterEmergencyAdmin {

    private static final Pattern LOGIN_NAME = Pattern.compile("[a-z][a-z0-9._-]{2,31}");

    public sealed interface Outcome {
        record Registered(IssuedEmergencyCredentials credentials, int usableAfter) implements Outcome {
        }

        record AlreadyRegistered(int usable) implements Outcome {
        }
    }

    private final AccountProvisioning provisioning;
    private final EmergencyAdminRepository admins;
    private final EmergencyBinding binding;
    private final EmergencyCredentialsFactory credentials;
    private final DomainEvents events;
    private final AuditLog audit;
    private final SingleOperatorMarks marks;

    RegisterEmergencyAdmin(AccountProvisioning provisioning, EmergencyAdminRepository admins, EmergencyBinding binding, EmergencyCredentialsFactory credentials, DomainEvents events,
            AuditLog audit, SingleOperatorMarks marks) {
        this.provisioning = provisioning;
        this.admins = admins;
        this.binding = binding;
        this.credentials = credentials;
        this.events = events;
        this.audit = audit;
        this.marks = marks;
    }

    @Transactional
    public Outcome execute(OperationAttestation attestation, EmployeeId employeeId, String loginName) {
        if (loginName == null || !LOGIN_NAME.matcher(loginName).matches()) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID, ErrorKind.UNPROCESSABLE,
                    "the login name is 3 to 32 characters: a lower-case letter, then lower-case letters, digits, dot, dash or underscore");
        }
        UserId person = provisioning.existingOrOpened(employeeId, AccountOpeningRoute.OPERATIONS).orElseThrow(() -> new DomainException(
                IdentityError.PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID, ErrorKind.UNPROCESSABLE,
                "employee " + employeeId.value() + " has no account and is not an employed person who could get one"));
        binding.requireValid(person);
        Optional<EmergencyAdmin> existing = admins.findByUserId(person);
        if (existing.isPresent()) {
            if (existing.get().loginName().equals(loginName)) {
                return new Outcome.AlreadyRegistered(binding.usableCount());
            }
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_EMERGENCY_ADMIN_DUPLICATE, ErrorKind.CONFLICT,
                    "user " + person.value() + " already has an emergency account under another login name");
        }
        EmergencyCredentialsFactory.Generated generated = credentials.generate(loginName);
        EmergencyAdmin admin = EmergencyAdmin.register(person, loginName, generated.passwordHash(), generated.sealedTotpSecret());
        if (!admins.insertIfAbsent(admin)) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_EMERGENCY_ADMIN_DUPLICATE, ErrorKind.CONFLICT,
                    "the login name " + loginName + " is taken");
        }
        events.publish(admin);
        AuditSubject subject = AuditSubject.of(AuditSubjectKind.EMERGENCY_ADMIN, person.value());
        audit.record(new AuditEntry("platform.identity.operations.emergency-admin.registered", null, null, subject, attestation.describe()));
        marks.witnessOmitted(attestation, subject);
        return new Outcome.Registered(generated.shown(), binding.usableCount());
    }
}
