package com.forcitis.one.acceptance.organization.workforce;

import java.util.List;

/**
 * #871 (IMP-1030-PAR-B2): every name the personnel command implementation must match, in one place so that a glossary rename
 * changes only this file. There is no REST endpoint and no due-date job in this phase (ADR-0068 item 2.5, line 99; card #872), so the entry
 * point is a Spring bean whose type, or one of whose interfaces, is {@link #API_TYPE} in {@link #API_PACKAGE}; see
 * {@link PersonnelCommandsProbe}. Names marked 暂名 come from the ADR and are listed as doubts in the card report.
 */
final class PersonnelCommandNames {

    private PersonnelCommandNames() {
    }

    // The entry point (the workforce api package).
    static final String API_PACKAGE = "com.forcitis.one.organization.workforce.api";
    static final String API_TYPE = "PersonnelCommands";
    static final String ENTER_METHOD = "enter";
    static final String WITHDRAW_METHOD = "withdraw";
    /** Simple names of the single record parameter of each method. */
    static final String ENTER_PARAMETER = "EnterPersonnelCommand";
    static final String WITHDRAW_PARAMETER = "WithdrawPersonnelCommand";

    // Record components of the parameters; typed ids are built from a UUID, enums from the constant names below.
    static final String EMPLOYEE_ID = "employeeId";
    static final String COMMAND_KIND = "commandKind";
    static final String EFFECTIVE_DATE = "effectiveDate";
    /** SEPARATE only: HR enters the last working day, the separation takes effect on the day after (ADR-0068 item 1.5). */
    static final String LAST_WORKING_DAY = "lastWorkingDay";
    static final String TARGET_ORG_UNIT_ID = "targetOrgUnitId";
    static final String TARGET_POSITION_ID = "targetPositionId";
    static final String IS_TARGET_FEE_EARNER = "isTargetFeeEarner";
    static final String TARGET_EMPLOYMENT_PHASE_KIND = "targetEmploymentPhaseKind";
    static final String PERSONNEL_COMMAND_ID = "personnelCommandId";
    static final String REASON = "reason";

    /** A component with any of these words (case-insensitive) would let the caller choose what the server decides (ADR-0068 items 1.2, 1.6). */
    static final List<String> CALLER_MUST_NOT_SUPPLY = List.of("createdby", "withdrawnby", "actor", "userid", "status", "appliedat", "withdrawnat");

    // Command kinds and statuses (ADR-0068 items 1.3, 1.4; the baseline CHECK values).
    static final String HIRE = "HIRE";
    static final String REGULARIZE = "REGULARIZE";
    static final String TRANSFER = "TRANSFER";
    static final String SEPARATE = "SEPARATE";
    static final String REINSTATE = "REINSTATE";
    static final String UNPAID_LEAVE_START = "UNPAID_LEAVE_START";
    static final String UNPAID_LEAVE_END = "UNPAID_LEAVE_END";
    static final String PENDING = "PENDING";
    static final String HELD = "HELD";
    static final String EFFECTIVE = "EFFECTIVE";
    static final String WITHDRAWN = "WITHDRAWN";
    static final String PROBATION = "PROBATION";
    static final String REGULAR = "REGULAR";
    static final String EMPLOYED = "EMPLOYED";
    static final String SEPARATED = "SEPARATED";

    // Permission (ADR-0068 item 1.6: one permission enters and withdraws; code format bc:resource:action, 暂名).
    static final String PERMISSION = "organization.workforce:personnel-command:create";

    /** The seeded system role of the 人事专员 (lead decision 2026-10-30; user decision of 2026-10-30, the decision file named 人事专员): holds PERMISSION. */
    static final String PERSONNEL_SPECIALIST_ROLE = "personnel_specialist";

    // Audit (ADR-0068 item 1.6 gives the withdrawn name; the entered name is a 暂名 by analogy).
    static final String AUDIT_ENTERED = "organization.workforce.personnel-command.entered";
    static final String AUDIT_WITHDRAWN = "organization.workforce.personnel-command.withdrawn";

    // Tables and columns of the workforce schema asserted on (baseline, and the signed-off shapes of #870 and #880).
    static final String COMMANDS = "personnel_commands";
    static final String EMPLOYEE_VERSIONS = "employee_versions";
    static final String ASSIGNMENT_VERSIONS = "assignment_versions";
    static final String SEPARATION_HOLDS = "separation_holds";
    static final String STATE_TRANSITIONS = "state_transitions";
    static final String AGGREGATE_PERSONNEL_COMMAND = "PersonnelCommand";
    static final String AGGREGATE_SEPARATION_HOLD = "SeparationHold";
    static final String ACTOR_TYPE_USER = "USER";

    // Events published when a command takes effect (contract A; the workforce event types).
    static final String EVENT_REGULARIZED = "EmployeeRegularized";
    static final String EVENT_TRANSFERRED = "EmployeeTransferred";
    static final String EVENT_SEPARATED = "EmployeeSeparated";
    static final String EVENT_REINSTATED = "EmployeeReinstated";
    static final List<String> ALL_COMMAND_EVENTS = List.of("EmployeeHired", EVENT_REGULARIZED, EVENT_TRANSFERRED, EVENT_SEPARATED, EVENT_REINSTATED,
            "SeparationHeld", "SeparationHoldReleased", "EmployeeCorrected");

    // ---- #873 (IMP-1030-PAR-B4): single release of a separation hold (ADR-0068 item 2.3) and the roles around it ----

    /** The release use case has no HTTP surface either; it is the bean named SeparationHolds in the same api package (暂名). */
    static final String HOLDS_API_TYPE = "SeparationHolds";
    static final String RELEASE_METHOD = "release";
    static final String RELEASE_PARAMETER = "ReleaseSeparationHold";
    /** Components of ReleaseSeparationHold: the hold and the reason. The releaser is the signed-in caller (ADR-0068 item 2.3), never a component. */
    static final String SEPARATION_HOLD_ID = "separationHoldId";

    static final String RELEASED = "RELEASED";
    static final String CHANNEL_INTERFACE = "INTERFACE";
    static final String RELEASE_PERMISSION = "organization.workforce:separation-hold:approve";
    static final String SUPER_ADMIN = "super_admin";
    static final String SECURITY_AUDITOR = "security_auditor";
    static final String AUDIT_HOLD_RELEASED = "organization.workforce.separation-hold.released";
    /**
     * The key text of the one advisory lock every separation entry, single release and withdrawal of a held command takes first
     * (ADR-0068 item 2.2, line 81: {@code pg_advisory_xact_lock(hashtextextended(<key>, 0))}, "键文本为 kernel 常量"). Agreed: a public static
     * String field of this name in this kernel class; looked up by reflection, missing = fail.
     */
    static final String LOCK_KEY_CLASS = "com.forcitis.one.kernel.SeparationReview";
    static final String LOCK_KEY_FIELD = "SEPARATION_LOCK_KEY";
    static final String EVENT_HELD = "SeparationHeld";
    static final String EVENT_HOLD_RELEASED = "SeparationHoldReleased";

    // ---- #931 (IMP-1030-PAR-C1): future-dated release goes PENDING; alert recipients (ruling on #732, 2026-10-30; ADR-0068 items 1.8, 2.3; ADR-0028 item 4) ----

    /**
     * 暂名 (doubt, one-design to confirm): the due-date job of ADR-0068 item 1.8 ("workforce self-declared @Scheduled + @SchedulerLock") has no name yet.
     * Agreed for the #931 tests: a Spring bean whose class is named DUE_JOB_TYPE, with a public no-argument method DUE_JOB_METHOD. What these tests
     * pin is only the job's handling of released future-dated separations: executes them on (not before) the effective date in the business day
     * zone, exactly once however often it runs, never re-holds them, never touches a still-HELD or WITHDRAWN command. The general due-date contract
     * for ordinary commands (a future TRANSFER or REGULARIZE becoming EFFECTIVE) and the job's lock order belong to C3's acceptance, not here.
     * The tests call the method directly, never through the scheduler or the lock, and with nobody signed in.
     */
    static final String DUE_JOB_TYPE = "PersonnelCommandDueJob";
    static final String DUE_JOB_METHOD = "run";
}
