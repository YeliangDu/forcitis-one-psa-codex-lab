package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.EmployeeExistence;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.platform.audit.api.AuditPartitions;
import java.io.PrintStream;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * The one-shot operations commands of workforce (ADR-0068 item 2.4): {@code release-separation-hold}, the release of a held separation when no
 * available super administrator would remain. It needs --separation-hold-id, --reason, --operator and a different --witness; the witness is always
 * required. Exit code, as identity's OperationsCommands: 0 done, 1 refused, 2 wrong usage. Runs with the profile workforce-operations.
 */
@Component
public class WorkforceOperationsCommands {

    static final String OPERATION = "operation";
    static final String RELEASE_SEPARATION_HOLD = "release-separation-hold";
    static final String HOLD_ID = "separation-hold-id";
    static final String REASON = "reason";
    static final String OPERATOR = "operator";
    static final String WITNESS = "witness";
    private static final Set<String> OWN_OPTIONS = Set.of(OPERATION, HOLD_ID, REASON, OPERATOR, WITNESS);

    private final ReleaseSeparationHoldByOperations releaseHold;
    private final EmployeeExistence employees;
    private final AuditPartitions auditPartitions;
    private final Clock clock;

    WorkforceOperationsCommands(ReleaseSeparationHoldByOperations releaseHold, EmployeeExistence employees, AuditPartitions auditPartitions, Clock clock) {
        this.releaseHold = releaseHold;
        this.employees = employees;
        this.auditPartitions = auditPartitions;
        this.clock = clock;
    }

    public int run(ApplicationArguments arguments, PrintStream out, PrintStream err) {
        if (!RELEASE_SEPARATION_HOLD.equals(single(arguments, OPERATION))) {
            err.println("usage: --operation=" + RELEASE_SEPARATION_HOLD + " --" + HOLD_ID + "=<id> --" + REASON + "=<text> --" + OPERATOR
                    + "=<employee id> --" + WITNESS + "=<employee id>");
            return 2;
        }
        try (WorkContext.Scope scope = WorkContext.enter(Uuids.timeOrdered(clock), null, "organization.workforce.operations")) {
            rejectUnknownOptions(arguments);
            SeparationHoldId hold = holdId(arguments);
            String reason = required(arguments, REASON);
            WorkforceOperationAttestation attestation = WorkforceOperationAttestation.of(single(arguments, OPERATOR), single(arguments, WITNESS));
            YearMonth month = YearMonth.from(clock.instant().atOffset(ZoneOffset.UTC));
            if (!auditPartitions.exists(month)) {
                err.println("refused: the audit partition for " + month + " does not exist, so no audit entry could be written. The resident application creates it ahead of time and "
                        + "this one-off command does not; start the resident application once or have operations create the partition, then run the command again");
                return 1;
            }
            if (!employees.exists(attestation.operator()) || !employees.exists(attestation.witness())) {
                throw WorkforceOperationAttestation.required();
            }
            releaseHold.execute(attestation, hold, reason);
            out.println("separation hold released");
            return 0;
        } catch (DomainException refused) {
            err.println("refused: " + refused.errorCode().code() + " - " + refused.getMessage());
            return 1;
        } catch (IllegalArgumentException wrongUsage) {
            err.println("wrong usage: " + wrongUsage.getMessage());
            return 2;
        }
    }

    /** A misspelt own option must not read as an absent witness; configuration options (dotted or environment-style names) are Spring's and pass. */
    private static void rejectUnknownOptions(ApplicationArguments arguments) {
        if (!arguments.getNonOptionArgs().isEmpty()) {
            throw new IllegalArgumentException("unexpected argument; options are written --name=value");
        }
        for (String name : arguments.getOptionNames()) {
            boolean configuration = name.contains(".") || name.matches("[A-Z][A-Z0-9_]*");
            if (!OWN_OPTIONS.contains(name) && (!configuration || OWN_OPTIONS.contains(name.toLowerCase(Locale.ROOT).replace('_', '-')))) {
                throw new IllegalArgumentException("unknown option --" + name);
            }
        }
    }

    private static SeparationHoldId holdId(ApplicationArguments arguments) {
        String value = required(arguments, HOLD_ID);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) {
            throw new IllegalArgumentException("--" + HOLD_ID + " must be the hold's id as a lower-case canonical UUID");
        }
        return new SeparationHoldId(id);
    }

    private static String required(ApplicationArguments arguments, String name) {
        String value = single(arguments, name);
        if (value == null) {
            throw new IllegalArgumentException("--" + name + " is required and must be given exactly once");
        }
        return value;
    }

    private static String single(ApplicationArguments arguments, String name) {
        var values = arguments.getOptionValues(name);
        return values == null || values.size() != 1 ? null : values.getFirst();
    }
}
