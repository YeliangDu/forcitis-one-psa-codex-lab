package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.SingleOperatorMode;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * The commands of ADR-0027 section 1 for operations: register, reset, disable and enable an emergency account, grant a role to a
 * person (the first administrators) and take one back. Each needs --operator and a different --witness, which the audit entry records; in the single operator mode the witness may be left out. A password and a
 * one-time-code secret exist only in the output stream handed in here, never in a log line, a file or an argument.
 * Exit code: 0 done or nothing to do, 1 refused, 2 wrong usage.
 */
@Component
public class OperationsCommands {

    static final String OPERATION = "operation";
    static final String EMPLOYEE_ID = "employee-id";
    static final String LOGIN_NAME = "login-name";
    static final String ROLE_CODE = "role-code";
    static final String OPERATOR = "operator";
    static final String WITNESS = "witness";
    private static final Set<String> OWN_OPTIONS = Set.of(OPERATION, EMPLOYEE_ID, LOGIN_NAME, ROLE_CODE, OPERATOR, WITNESS);

    private final RegisterEmergencyAdmin register;
    private final ResetEmergencyAdminCredentials reset;
    private final DisableEmergencyAdmin disable;
    private final EnableEmergencyAdmin enable;
    private final GrantRoleByOperations grant;
    private final RevokeRoleByOperations revoke;
    private final SingleOperatorMode mode;
    private final OrgDirectory orgDirectory;

    OperationsCommands(RegisterEmergencyAdmin register, ResetEmergencyAdminCredentials reset, DisableEmergencyAdmin disable,
            EnableEmergencyAdmin enable, GrantRoleByOperations grant, RevokeRoleByOperations revoke, SingleOperatorMode mode,
            OrgDirectory orgDirectory) {
        this.register = register;
        this.reset = reset;
        this.disable = disable;
        this.enable = enable;
        this.grant = grant;
        this.revoke = revoke;
        this.mode = mode;
        this.orgDirectory = orgDirectory;
    }

    public int run(ApplicationArguments arguments, PrintStream out, PrintStream err) {
        String operation = single(arguments, OPERATION);
        if (operation == null) {
            err.println("usage: --operation=<register-emergency-admin|reset-emergency-admin-credentials|disable-emergency-admin|"
                    + "enable-emergency-admin|grant-role|revoke-role> --employee-id=<id> [--login-name=<name>] [--role-code=<code>] --operator=<employee id> [--witness=<employee id>; may be left out in the single operator mode]");
            return 2;
        }
        try {
            rejectUnknownOptions(arguments);
        } catch (IllegalArgumentException wrongUsage) {
            err.println("wrong usage: " + wrongUsage.getMessage());
            return 2;
        }
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "platform.identity.operations")) {
            OperationAttestation attestation = attestation(arguments);
            requireExisting(attestation);
            EmployeeId employeeId = employeeId(arguments);
            return switch (operation) {
                case "register-emergency-admin" -> registerAdmin(attestation, employeeId, single(arguments, LOGIN_NAME), out);
                case "reset-emergency-admin-credentials" -> show(reset.execute(attestation, employeeId), out, "credentials reset");
                case "disable-emergency-admin" -> done(disable.execute(attestation, employeeId), out, "disabled", "already disabled, nothing changed");
                case "enable-emergency-admin" -> done(enable.execute(attestation, employeeId), out, "enabled", "already enabled, nothing changed");
                case "grant-role" -> done(grant.execute(attestation, employeeId, single(arguments, ROLE_CODE)), out, "role granted",
                        "the role is already held, nothing changed");
                case "revoke-role" -> done(revoke.execute(attestation, employeeId, single(arguments, ROLE_CODE)), out, "role revoked",
                        "the role is not held, nothing changed");
                default -> {
                    err.println("unknown operation " + operation);
                    yield 2;
                }
            };
        } catch (DomainException refused) {
            err.println("refused: " + refused.errorCode().code() + " - " + refused.getMessage());
            return 1;
        } catch (IllegalArgumentException wrongUsage) {
            err.println("wrong usage: " + wrongUsage.getMessage());
            return 2;
        }
    }

    /** Own options are the six above; configuration options (dotted names such as spring.profiles.include, or environment-style names) are Spring's and pass. A misspelt own option must not read as an absent witness. */
    private static void rejectUnknownOptions(ApplicationArguments arguments) {
        if (!arguments.getNonOptionArgs().isEmpty()) {
            throw new IllegalArgumentException("unexpected argument; options are written --name=value");
        }
        for (String name : arguments.getOptionNames()) {
            if (OWN_OPTIONS.contains(name)) {
                continue;
            }
            boolean configuration = name.contains(".") || name.matches("[A-Z][A-Z0-9_]*");
            if (!configuration || OWN_OPTIONS.contains(name.toLowerCase(Locale.ROOT).replace('_', '-'))) {
                throw new IllegalArgumentException("unknown option --" + name);
            }
        }
    }

    /** The witness may be left out only in the single operator mode and only when the option is not on the command line at all; given twice, empty or invalid it is refused. */
    private OperationAttestation attestation(ApplicationArguments arguments) {
        if (mode.enabled() && !arguments.containsOption(WITNESS)) {
            return OperationAttestation.withoutWitness(single(arguments, OPERATOR));
        }
        return OperationAttestation.of(single(arguments, OPERATOR), single(arguments, WITNESS));
    }

    /** Separated employees still count: the snapshot keeps them, and the audit trail must be able to name them. The witness is checked only when one was given. */
    private void requireExisting(OperationAttestation attestation) {
        if (orgDirectory.findEmployee(attestation.operator()).isEmpty()
                || (!attestation.witnessOmitted() && orgDirectory.findEmployee(attestation.witness()).isEmpty())) {
            throw OperationAttestation.required();
        }
    }

    private int registerAdmin(OperationAttestation attestation, EmployeeId employeeId, String loginName, PrintStream out) {
        switch (register.execute(attestation, employeeId, loginName)) {
            case RegisterEmergencyAdmin.Outcome.Registered registered -> {
                show(registered.credentials(), out, "emergency account registered");
                warnIfTooFew(registered.usableAfter(), out);
            }
            case RegisterEmergencyAdmin.Outcome.AlreadyRegistered already -> {
                out.println("already registered under this login name, nothing changed and no new credentials issued");
                warnIfTooFew(already.usable(), out);
            }
        }
        return 0;
    }

    private void warnIfTooFew(int usable, PrintStream out) {
        int minimum = ReconcileEmergencyAdmins.minimumUsable(mode);
        if (usable < minimum) {
            out.println(minimum == ReconcileEmergencyAdmins.MINIMUM_USABLE
                    ? "WARNING: only " + usable + " usable emergency account(s); at least 2, bound to different super_admin holders, are required"
                    : "WARNING: only " + usable + " usable emergency account(s); at least " + minimum + ", bound to a super_admin holder, is required");
        }
    }

    private static int show(IssuedEmergencyCredentials credentials, PrintStream out, String what) {
        out.println(what + " for login name " + credentials.loginName());
        out.println("password                       : " + credentials.password());
        out.println("one-time-code secret (base32)  : " + credentials.totpSecretBase32());
        out.println("authenticator link             : otpauth://totp/" + URLEncoder.encode("FORCITIS one:" + credentials.loginName(), StandardCharsets.UTF_8)
                + "?secret=" + credentials.totpSecretBase32() + "&issuer=" + URLEncoder.encode("FORCITIS one", StandardCharsets.UTF_8));
        out.println("These are shown once and stored only as a hash and an encrypted secret. Hand them over now; do not copy this output into a file or a chat.");
        return 0;
    }

    private static int done(boolean changed, PrintStream out, String changedMessage, String unchangedMessage) {
        out.println(changed ? changedMessage : unchangedMessage);
        return 0;
    }

    private static EmployeeId employeeId(ApplicationArguments arguments) {
        String value = single(arguments, EMPLOYEE_ID);
        if (value == null) {
            throw new IllegalArgumentException("--employee-id must be the employee's id");
        }
        try {
            return EmployeeId.parse(value);
        } catch (IllegalArgumentException notCanonical) {
            throw new IllegalArgumentException("--employee-id must be the employee's id as a lower-case canonical UUID");
        }
    }

    private static String single(ApplicationArguments arguments, String name) {
        var values = arguments.getOptionValues(name);
        if (values == null || values.size() != 1) {
            return null;
        }
        return values.getFirst();
    }
}
