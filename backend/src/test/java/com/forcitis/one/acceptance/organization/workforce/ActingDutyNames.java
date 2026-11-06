package com.forcitis.one.acceptance.organization.workforce;

/** #1566: command kinds and event names from the card; components (camelCase of the signed columns) and table from the signed schema. */
final class ActingDutyNames {
    private ActingDutyNames() {
    }

    static final String ACTING_START = "ACTING_START";
    static final String ACTING_END = "ACTING_END";
    static final String ACTING_EXTEND = "ACTING_EXTEND";
    static final String ACTING_COVERED_ORG_UNIT_ID = "actingCoveredOrgUnitId";
    static final String ACTING_COVERED_POSITION_ID = "actingCoveredPositionId";
    static final String ACTING_COVERED_EMPLOYEE_ID = "actingCoveredEmployeeId";
    static final String ACTING_ENDS_BEFORE_DATE = "actingEndsBeforeDate";
    static final String TARGET_ACTING_DUTY_ID = "targetActingDutyId";
    static final String EVENT_ACTING_STARTED = "EmployeeActingDutyStarted";
    static final String EVENT_ACTING_ENDED = "EmployeeActingDutyEnded";
    static final String ACTING_DUTIES = "acting_duties";
}
