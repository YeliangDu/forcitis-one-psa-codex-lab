package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.IdentityError;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * The commands of ADR-0027 section 1 for operations: register, reset, disable and enable an emergency account, and grant a role to a
 * person (the first administrators). Each needs --operator and a different --witness, which the audit entry records. A password and a
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

    private final RegisterEmergencyAdmin register;
    private final ResetEmergencyAdminCredentials reset;
    private final DisableEmergencyAdmin disable;
    private final EnableEmergencyAdmin enable;
    private final GrantRoleByOperations grant;
    private final OrgDirectory orgDirectory;

    OperationsCommands(RegisterEmergencyAdmin register, ResetEmergencyAdminCredentials reset, DisableEmergencyAdmin disable,
            EnableEmergencyAdmin enable, GrantRoleByOperations grant, OrgDirectory orgDirectory) {
        this.register = register;
        this.reset = reset;
        this.disable = disable;
        this.enable = enable;
        this.grant = grant;
        this.orgDirectory = orgDirectory;
    }

    public int run(ApplicationArguments arguments, PrintStream out, PrintStream err) {
        String operation = single(arguments, OPERATION);
        if (operation == null) {
            err.println("usage: --operation=<register-emergency-admin|reset-emergency-admin-credentials|disable-emergency-admin|"
                    + "enable-emergency-admin|grant-role> --employee-id=<id> [--login-name=<name>] [--role-code=<code>] --operator=<employee id> --witness=<employee id>");
            return 2;
        }
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "platform.identity.operations")) {
            OperationAttestation attestation = OperationAttestation.of(single(arguments, OPERATOR), single(arguments, WITNESS));
            requireExisting(attestation);
            EmployeeId employeeId = employeeId(arguments);
            return switch (operation) {
                case "register-emergency-admin" -> registerAdmin(attestation, employeeId, single(arguments, LOGIN_NAME), out);
                case "reset-emergency-admin-credentials" -> show(reset.execute(attestation, employeeId), out, "credentials reset");
                case "disable-emergency-admin" -> done(disable.execute(attestation, employeeId), out, "disabled", "already disabled, nothing changed");
                case "enable-emergency-admin" -> done(enable.execute(attestation, employeeId), out, "enabled", "already enabled, nothing changed");
                case "grant-role" -> done(grant.execute(attestation, employeeId, single(arguments, ROLE_CODE)), out, "role granted",
                        "the role is already held, nothing changed");
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

    /** Separated employees still count: the snapshot keeps them, and the audit trail must be able to name them. */
    private void requireExisting(OperationAttestation attestation) {
        if (orgDirectory.findEmployee(attestation.operator()).isEmpty() || orgDirectory.findEmployee(attestation.witness()).isEmpty()) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED, ErrorKind.UNPROCESSABLE,
                    "an operations command needs an operator and a witness who are two different existing employees, each given as an employee id");
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

    private static void warnIfTooFew(int usable, PrintStream out) {
        if (usable < 2) {
            out.println("WARNING: only " + usable + " usable emergency account(s); at least 2, bound to different super_admin holders, are required");
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
