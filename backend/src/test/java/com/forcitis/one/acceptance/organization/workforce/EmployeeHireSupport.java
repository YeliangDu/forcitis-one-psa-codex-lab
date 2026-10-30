package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ground of the #959 tests: a HIRE of a new person through the personnel command entry (no employee id, the profile as components, no
 * employee code), counts of what a refused call must not leave behind, and direct writes of the employees root the tests need as obstacles.
 */
abstract class EmployeeHireSupport extends PersonnelCommandSupport {

    /** The whole HIRE of a new person; a null component is left out. */
    protected UUID hire(String displayName, String gender, String email, String wecomUserId) throws Exception {
        Map<String, Object> fields = new HashMap<>();
        fields.put(COMMAND_KIND, HIRE);
        fields.put(EFFECTIVE_DATE, TODAY);
        fields.put(TARGET_ORG_UNIT_ID, unit);
        fields.put(TARGET_POSITION_ID, newPosition());
        fields.put(IS_TARGET_FEE_EARNER, true);
        fields.put(TARGET_EMPLOYMENT_PHASE_KIND, PROBATION);
        fields.put(DISPLAY_NAME, displayName);
        fields.put(GENDER, gender);
        fields.put(EMAIL, email);
        fields.put(WECOM_USER_ID, wecomUserId);
        fields.values().removeIf(java.util.Objects::isNull);
        return commands().enter(fields);
    }

    /** The employee a HIRE command was entered for. */
    protected UUID employeeOf(UUID command) throws Exception {
        return fromStored(command(command).get("employee_id"));
    }

    protected Map<String, Object> root(UUID employee) throws Exception {
        return rows("select * from employees where id = ?", stored(employee)).get(0);
    }

    /** Rows of the workforce tables a hire writes: a refused call must leave every count as it was. */
    protected record Counts(long employees, long commands, long employeeVersions, long assignmentVersions) {
    }

    protected Counts counts() throws Exception {
        return new Counts(count("employees"), count(COMMANDS), count(EMPLOYEE_VERSIONS), count(ASSIGNMENT_VERSIONS));
    }

    private long count(String table) throws Exception {
        return (long) rows("select count(*) as n from " + table).get(0).get("n");
    }

    /** A root row only, as a migrated person or an obstacle: the code and WeCom user id are chosen by the test. */
    protected UUID rootWith(String code, String wecomUserId, String status) throws Exception {
        UUID id = newEmployeeId();
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "insert into employees (id, employee_code, display_name, wecom_user_id, employment_status) values (?, ?, ?, ?, ?)",
                    stored(id), code, "Synthetic obstacle " + serialOf(id), wecomUserId, status);
        }
        return id;
    }

    protected void deleteRoot(UUID id) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "delete from employees where id = ?", stored(id));
        }
    }

    protected static String syntheticWecom(String tag) {
        return "synthetic-" + tag + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
