package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.Availability;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ground of the #1371 scenario tests (docs/architecture/proposals/employment-relationship.md, sections 5, 6.1 to 6.3). The new command
 * components are the ruled names of the lead ruling on #1371; no new Java type is referenced, so that this compiles before the
 * implementation exists. People are made through the HIRE command, never by writing employee versions directly.
 */
abstract class EmploymentRelationshipSupport extends FutureHoldSupport {

    protected static final String TARGET_EMPLOYER_LEGAL_ENTITY_ID = "targetEmployerLegalEntityId";
    protected static final String PREDECESSOR_RELATIONSHIP_ID = "predecessorRelationshipId";
    protected static final String EMPLOYMENT_RELATIONSHIP_ID = "employmentRelationshipId";
    protected static final String EVENT_STARTED = "EmploymentRelationshipStarted";
    protected static final String EVENT_ENDED = "EmploymentRelationshipEnded";
    protected static final String PRIMARY_ON_ENDING = "ORGANIZATION_WORKFORCE_PRIMARY_ASSIGNMENT_ON_ENDING_RELATIONSHIP";

    @Autowired
    private OrgDirectory directory;

    /** A second employer: a legal entity other than the world's. */
    protected UUID otherEmployer() throws Exception {
        return createLegalEntity(administrator, newLegalEntityCode("ER"), "Synthetic other employer");
    }

    /** HIRE of a new person at the world's legal entity, effective today, with a primary post (design 5, HIRE). */
    protected UUID hireNewPerson(UUID employer) throws Exception {
        actIn(hr);
        Map<String, Object> fields = hireFields(employer);
        fields.put(DISPLAY_NAME, "Synthetic relationship " + UUID.randomUUID().toString().substring(0, 8));
        fields.put(TARGET_ORG_UNIT_ID, unit);
        fields.put(TARGET_POSITION_ID, newPosition());
        return commands().enter(fields);
    }

    /** The employee a HIRE entered for a new person created. */
    protected UUID personOf(UUID hireCommand) throws Exception {
        return fromStored(command(hireCommand).get("employee_id"));
    }

    /** HIRE of an existing person by another employer (design 5): no primary post, the person keeps hers. */
    protected UUID hireAdditionalEmployer(UUID person, UUID employer, LocalDate effective, UUID predecessor) {
        Map<String, Object> fields = hireFields(employer);
        fields.put(EMPLOYEE_ID, person);
        fields.put(EFFECTIVE_DATE, effective);
        if (predecessor != null) {
            fields.put(PREDECESSOR_RELATIONSHIP_ID, predecessor);
        }
        return commands().enter(fields);
    }

    private static Map<String, Object> hireFields(UUID employer) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(COMMAND_KIND, HIRE);
        fields.put(EFFECTIVE_DATE, TODAY);
        fields.put(IS_TARGET_FEE_EARNER, false);
        fields.put(TARGET_EMPLOYMENT_PHASE_KIND, PROBATION);
        fields.put(TARGET_EMPLOYER_LEGAL_ENTITY_ID, employer);
        return fields;
    }

    /** SEPARATE of one relationship (design 5); the separation takes effect the day after the last working day. */
    protected UUID endRelationship(UUID person, UUID relationship, LocalDate lastWorkingDay) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, person);
        fields.put(COMMAND_KIND, SEPARATE);
        fields.put(LAST_WORKING_DAY, lastWorkingDay);
        fields.put(EMPLOYMENT_RELATIONSHIP_ID, relationship);
        return commands().enter(fields);
    }

    /** UNPAID_LEAVE_START of one relationship, effective on the day. */
    protected UUID startUnpaidLeave(UUID person, UUID relationship, LocalDate effective) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, person);
        fields.put(COMMAND_KIND, UNPAID_LEAVE_START);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(EMPLOYMENT_RELATIONSHIP_ID, relationship);
        return commands().enter(fields);
    }

    /** The relationship of the person with the employer (null when there is none). */
    protected UUID relationshipOf(UUID person, UUID employer) throws Exception {
        List<Map<String, Object>> found = rows("select employment_relationship_id from employment_relationships where employee_id = ? and employer_legal_entity_id = ?",
                stored(person), stored(employer));
        return found.isEmpty() ? null : fromStored(found.get(0).get("employment_relationship_id"));
    }

    protected List<Map<String, Object>> relationshipsOf(UUID person) throws Exception {
        return rows("select * from employment_relationships where employee_id = ? order by employment_relationship_id", stored(person));
    }

    /** The person-level verdict kept on the root row (design 4.1: the snapshot columns stay derived from the person-level verdict). */
    protected String statusOf(UUID person) throws Exception {
        return (String) rows("select employment_status from employees where id = ?", stored(person)).get(0).get("employment_status");
    }

    protected Availability availability(UUID person, LocalDate day) {
        return directory.availabilityOn(employeeIdOf(person), day);
    }

    /** Runs the due-date job on the day (the clock moves there first). */
    protected void dueOn(LocalDate day) {
        setToday(day);
        runDueJob();
    }

    protected void assertNothingChanged(Map<String, Long> before, String why) throws Exception {
        assertThat(counts(before.keySet())).as(why).isEqualTo(before);
    }

    protected Map<String, Long> counts(java.util.Set<String> tables) throws Exception {
        Map<String, Long> counts = new java.util.TreeMap<>();
        for (String table : tables) {
            counts.put(table, (long) rows("select count(*) as n from " + table).get(0).get("n"));
        }
        return counts;
    }

    protected Map<String, Long> snapshot() throws Exception {
        return counts(java.util.Set.of("employees", "employment_relationships", "employment_relationship_versions", "personnel_commands", "assignment_versions"));
    }
}
