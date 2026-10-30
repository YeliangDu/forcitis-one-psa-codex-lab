package com.forcitis.one.acceptance.organization.workforce;

import java.util.regex.Pattern;

/**
 * #877 IMP-1030-PAR-B8 (acceptance cases of #876): every name the workforce ops command {@code release-separation-hold} must match, in one place so
 * that a rename changes only this file. The command is a one-shot operations command like the identity ones (ADR-0068 item 2.4, line 93 to 97): a
 * Spring bean in the workforce application package whose method {@code run(ApplicationArguments, PrintStream out, PrintStream err)} answers the
 * exit code (0 done, 1 refused, 2 wrong usage, as {@code OperationsCommands} of identity). The bean is looked up at run time by type name, so this
 * package compiles before the command exists; a missing bean fails the tests, it never skips them. Names marked 暂名 are listed as doubts in the
 * card report.
 */
final class SeparationHoldOperationNames {

    private SeparationHoldOperationNames() {
    }

    /** The runner: workforce may not use identity's OperationAttestation (module-dependencies.txt), so it has its own. Simple name differs from identity's OperationsCommands so the bean names do not clash. */
    static final String RUNNER_PACKAGE = "com.forcitis.one.organization.workforce.application";
    static final String RUNNER_TYPE = "WorkforceOperationsCommands";
    static final String RUN_METHOD = "run";

    /** The command line, as identity's: {@code --operation=release-separation-hold --separation-hold-id=<uuid> --reason=<text> --operator=<employee id> --witness=<employee id>}. */
    static final String OPERATION_OPTION = "operation";
    static final String COMMAND_NAME = "release-separation-hold";
    static final String HOLD_OPTION = "separation-hold-id";
    static final String REASON_OPTION = "reason";
    static final String OPERATOR_OPTION = "operator";
    static final String WITNESS_OPTION = "witness";

    static final int DONE = 0;
    static final int REFUSED = 1;
    static final int WRONG_USAGE = 2;

    /** The release channel of the hold (ADR-0068 item 2.1, line 79, DDL column release_channel_kind). */
    static final String CHANNEL_OPERATIONS = "OPERATIONS_COMMAND";

    /** The audit of the single operator mode of identity: a workforce release must never write it (ADR-0068 item 2.4, line 96). */
    static final String SINGLE_OPERATOR_AUDIT_PREFIX = "platform.identity.single-operator-mode";

    /** The refusal when 1 or more available super administrators would remain points to the release on the interface (user ruling 2026-10-30). */
    static final Pattern POINTS_TO_INTERFACE = Pattern.compile("(?i)interface|\\bUI\\b|界面");

    /** The remaining available super administrators after the release, as a field of the audit detail ("remaining ...: 0"). */
    static final Pattern REMAINING_ZERO = Pattern.compile("(?is)(remaining|剩余)\\D{0,40}0(\\D|$)");
    /** The session or route field of the audit says the release came from the ops command (ADR-0068 item 2.3 line 91 has the session mode; for this path ADR-0068 line 95 says 途径记为运维命令). 暂名 */
    static final Pattern ROUTE_OPERATIONS = Pattern.compile("(?i)operations[ _-]?command");
}
