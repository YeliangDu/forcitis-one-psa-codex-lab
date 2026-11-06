package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.ActingDutyNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.function.Executable;

/**
 * #1566: real command entry and due job, with readbacks of the signed acting-duty schema.
 * No acting rows or commands are seeded: every duty must first be accepted by the production path.
 */
abstract class ActingDutySupport extends FutureHoldSupport {

    protected Map<String, Object> startFields(UUID employee, UUID coveredUnit, UUID position, UUID coveredEmployee,
            LocalDate starts, LocalDate ends) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, employee);
        fields.put(COMMAND_KIND, ACTING_START);
        fields.put(EFFECTIVE_DATE, starts);
        fields.put(ACTING_COVERED_ORG_UNIT_ID, coveredUnit);
        fields.put(ACTING_COVERED_POSITION_ID, position);
        fields.put(ACTING_COVERED_EMPLOYEE_ID, coveredEmployee);
        fields.put(ACTING_ENDS_BEFORE_DATE, ends);
        fields.put(REASON, "synthetic: temporary coverage");
        return fields;
    }

    protected UUID start(UUID employee, UUID coveredUnit, UUID position, LocalDate starts, LocalDate ends) {
        return commands().enter(startFields(employee, coveredUnit, position, null, starts, ends));
    }

    protected Map<String, Object> changeFields(UUID employee, String kind, UUID duty, LocalDate effective, LocalDate ends) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, employee);
        fields.put(COMMAND_KIND, kind);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(TARGET_ACTING_DUTY_ID, duty);
        if (ends != null) {
            fields.put(ACTING_ENDS_BEFORE_DATE, ends);
        }
        fields.put(REASON, "synthetic: adjust temporary coverage");
        return fields;
    }

    protected UUID end(UUID employee, UUID duty, LocalDate day) {
        return commands().enter(changeFields(employee, ACTING_END, duty, day, null));
    }

    protected UUID extend(UUID employee, UUID duty, LocalDate day, LocalDate ends) {
        return commands().enter(changeFields(employee, ACTING_EXTEND, duty, day, ends));
    }

    protected UUID dutyOf(UUID command) throws Exception {
        List<Map<String, Object>> found = rows("select * from " + ACTING_DUTIES + " where source_personnel_command_id = ?", command);
        assertThat(found).as("one duty written by the effective ACTING_START").hasSize(1);
        return (UUID) found.getFirst().get("acting_duty_id");
    }

    protected Map<String, Object> duty(UUID id) throws Exception {
        List<Map<String, Object>> found = rows("select * from " + ACTING_DUTIES + " where acting_duty_id = ?", id);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    protected List<Map<String, Object>> duties(UUID employee) throws Exception {
        return rows("select * from " + ACTING_DUTIES + " where employee_id = ? order by acting_duty_id", stored(employee));
    }

    protected void assertOpen(UUID duty, LocalDate starts, LocalDate ends) throws Exception {
        assertThat(duty(duty)).containsEntry("starts_on_date", starts).containsEntry("ends_before_date", ends)
                .containsEntry("ended_on_date", null).containsEntry("end_cause_kind", null);
    }

    protected void assertEnded(UUID duty, LocalDate ended, String cause) throws Exception {
        assertThat(duty(duty)).containsEntry("ended_on_date", ended).containsEntry("end_cause_kind", cause);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isEqualTo(1);
    }

    protected void advanceTo(LocalDate day) throws Exception {
        setToday(day);
        hr = signedInAgain(hr);
        actIn(hr);
    }

    protected void refused(UUID employee, Executable entry) throws Exception {
        long before = commandCount(employee);
        List<Map<String, Object>> beforeDuties = duties(employee);
        assertThatThrownBy(entry::execute).isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(isPermissionRefusal(failure)).isFalse());
        assertThat(commandCount(employee)).as("refusal creates no command").isEqualTo(before);
        assertThat(duties(employee)).as("refusal leaves duties unchanged").isEqualTo(beforeDuties);
    }

    /** A5 explicitly requires read-only approval counts; no approval state is prepared or modified. */
    protected List<Map<String, Object>> approvalCounts() throws Exception {
        try (Connection approval = schemaConnection("platform_approval")) {
            return query(approval, """
                    select (select count(*) from approval_tasks) as tasks,
                           (select count(*) from approval_requests) as requests
                    """);
        }
    }
}
