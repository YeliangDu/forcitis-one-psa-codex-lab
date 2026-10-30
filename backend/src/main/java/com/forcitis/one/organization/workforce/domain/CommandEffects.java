package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What each kind of command sets on the two version tables (ADR-0068 item 1.3, W2 design section 3), by column name. A column a command does
 * not name is not its business: it keeps whatever the timeline holds. A separation also ends the assignment, which is not a column and is
 * handled by {@link VersionPlanner#endAt}.
 */
public final class CommandEffects {
    public static final String EMPLOYMENT = "employment_status";
    public static final String PHASE = "employment_phase_kind";
    public static final String ON_LEAVE = "is_on_unpaid_leave";
    public static final String FEE_EARNER = "is_fee_earner";
    public static final String START_DATE = "employment_start_date";
    public static final String START_BASIS = "employment_start_basis_kind";
    public static final Set<String> EMPLOYEE_COLUMNS = Set.of(EMPLOYMENT, PHASE, ON_LEAVE, FEE_EARNER, START_DATE, START_BASIS);

    public static final String ORG_UNIT = "org_unit_id";
    public static final String POSITION = "position_id";
    public static final String REPORTS_TO = "reports_to_employee_id";
    public static final Set<String> ASSIGNMENT_COLUMNS = Set.of(ORG_UNIT, POSITION, REPORTS_TO);

    private static final Predicate<VersionRow> ANY = row -> true;
    private static final Predicate<VersionRow> EMPLOYED = row -> "EMPLOYED".equals(row.values().get(EMPLOYMENT));

    private CommandEffects() {
    }

    /** The employee version columns the command sets, with their values; a value may be null (a separated version has no phase). */
    public static Map<String, Object> employeeValues(PersonnelCommand c) {
        Map<String, Object> v = new LinkedHashMap<>();
        switch (c.kind()) {
            case HIRE, REINSTATE -> {
                v.put(EMPLOYMENT, "EMPLOYED");
                v.put(PHASE, Objects.requireNonNull(c.targetEmploymentPhaseKind(), "phase").name());
                v.put(ON_LEAVE, false);
                v.put(FEE_EARNER, c.isTargetFeeEarner());
                v.put(START_DATE, c.effectiveDate());
                v.put(START_BASIS, "DECLARED");
            }
            case SEPARATE -> {
                v.put(EMPLOYMENT, "SEPARATED");
                v.put(PHASE, null);
                v.put(ON_LEAVE, false);
                v.put(START_DATE, null);
                v.put(START_BASIS, null);
            }
            case REGULARIZE -> v.put(PHASE, "REGULAR");
            case UNPAID_LEAVE_START -> v.put(ON_LEAVE, true);
            case UNPAID_LEAVE_END -> v.put(ON_LEAVE, false);
            case TRANSFER, CORRECTION -> {
                if (c.isTargetFeeEarner() != null) {
                    v.put(FEE_EARNER, c.isTargetFeeEarner());
                }
                if (c.targetEmploymentPhaseKind() != null) {
                    v.put(PHASE, c.targetEmploymentPhaseKind().name());
                }
            }
        }
        return v;
    }

    /** The assignment version columns the command sets. */
    public static Map<String, Object> assignmentValues(PersonnelCommand c) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (c.kind() == PersonnelCommandKind.HIRE || c.kind() == PersonnelCommandKind.REINSTATE) {
            v.put(ORG_UNIT, Objects.requireNonNull(c.targetOrgUnitId(), "department").value());
            v.put(POSITION, Objects.requireNonNull(c.targetPositionId(), "position").value());
            v.put(REPORTS_TO, c.targetReportsToEmployeeId() == null ? null : c.targetReportsToEmployeeId().value());
        } else if (c.kind() == PersonnelCommandKind.TRANSFER || c.kind() == PersonnelCommandKind.CORRECTION) {
            if (c.targetOrgUnitId() != null) {
                v.put(ORG_UNIT, c.targetOrgUnitId().value());
            }
            if (c.targetPositionId() != null) {
                v.put(POSITION, c.targetPositionId().value());
            }
            if (c.targetReportsToEmployeeId() != null) {
                v.put(REPORTS_TO, c.targetReportsToEmployeeId().value());
            }
        }
        return v;
    }

    /** Rows the command may change on the employee table: a phase or a leave flag only exists on an employed version. */
    public static Predicate<VersionRow> employeeFilter(PersonnelCommand c) {
        return switch (c.kind()) {
            case HIRE, REINSTATE, SEPARATE -> ANY;
            case REGULARIZE, UNPAID_LEAVE_START, UNPAID_LEAVE_END, TRANSFER, CORRECTION -> EMPLOYED;
        };
    }

    /** A hire or a reinstatement starts rows on a day that no current row covers. */
    public static boolean coversEmptyDay(PersonnelCommand c) {
        return c.kind() == PersonnelCommandKind.HIRE || c.kind() == PersonnelCommandKind.REINSTATE;
    }

    /** A separation ends the assignment instead of changing a column of it. */
    public static boolean endsAssignment(PersonnelCommand c) {
        return c.kind() == PersonnelCommandKind.SEPARATE;
    }
}
