package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ground of the #1371 command-binding tests (employment-relationship design (ADR-0086) sections 4.2-8, 5 and 6.3): commands that name their
 * relationship, build on the ruled input names of {@link EmploymentRelationshipSupport}. Types are reached through the personnel command probe,
 * so this compiles before the implementation exists.
 */
abstract class EmploymentRelationshipBindingSupport extends EmploymentRelationshipSupport {

    protected static final String START_CONCURRENT = "START_CONCURRENT";
    protected static final String END_CONCURRENT = "END_CONCURRENT";
    protected static final String TARGET_ASSIGNMENT_ID = "targetAssignmentId";

    /** HIRE of an existing person by another employer that also carries a primary post (both target columns set, design 4.1) and no predecessor. */
    protected UUID hireWithPost(UUID person, UUID employer, LocalDate effective, UUID orgUnit, UUID position) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(COMMAND_KIND, HIRE);
        fields.put(EMPLOYEE_ID, person);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(IS_TARGET_FEE_EARNER, false);
        fields.put(TARGET_EMPLOYMENT_PHASE_KIND, PROBATION);
        fields.put(TARGET_EMPLOYER_LEGAL_ENTITY_ID, employer);
        fields.put(TARGET_ORG_UNIT_ID, orgUnit);
        fields.put(TARGET_POSITION_ID, position);
        return commands().enter(fields);
    }

    /** REINSTATE of one ended relationship with a primary post (design 5, REINSTATE). */
    protected UUID reinstateIn(UUID person, UUID relationship, LocalDate effective, UUID orgUnit, UUID position) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(COMMAND_KIND, REINSTATE);
        fields.put(EMPLOYEE_ID, person);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(EMPLOYMENT_RELATIONSHIP_ID, relationship);
        fields.put(TARGET_ORG_UNIT_ID, orgUnit);
        fields.put(TARGET_POSITION_ID, position);
        fields.put(IS_TARGET_FEE_EARNER, false);
        fields.put(TARGET_EMPLOYMENT_PHASE_KIND, PROBATION);
        return commands().enter(fields);
    }

    /** TRANSFER naming a relationship; root null acts on the primary root, else on the given root (design 5, TRANSFER). */
    protected UUID transferIn(UUID person, UUID relationship, LocalDate day, UUID root, UUID toUnit) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(COMMAND_KIND, TRANSFER);
        fields.put(EMPLOYEE_ID, person);
        fields.put(EFFECTIVE_DATE, day);
        fields.put(EMPLOYMENT_RELATIONSHIP_ID, relationship);
        fields.put(TARGET_ORG_UNIT_ID, toUnit);
        if (root != null) {
            fields.put(TARGET_ASSIGNMENT_ID, root);
        }
        return commands().enter(fields);
    }

    /** START_CONCURRENT in the relationship that employs the person in the new role (design 5). */
    protected UUID startConcurrentIn(UUID person, UUID relationship, LocalDate day, UUID orgUnit, UUID position) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(COMMAND_KIND, START_CONCURRENT);
        fields.put(EMPLOYEE_ID, person);
        fields.put(EFFECTIVE_DATE, day);
        fields.put(EMPLOYMENT_RELATIONSHIP_ID, relationship);
        fields.put(TARGET_ORG_UNIT_ID, orgUnit);
        fields.put(TARGET_POSITION_ID, position);
        return commands().enter(fields);
    }

    protected UUID primaryRootOf(UUID person) throws Exception {
        return fromStored(rows("select assignment_id from employee_assignments where employee_id = ? and assignment_kind = 'PRIMARY'", stored(person)).get(0).get("assignment_id"));
    }

    /** The CONCURRENT root a START_CONCURRENT created (the root of the version the command wrote). */
    protected UUID rootCreatedBy(UUID command) throws Exception {
        List<Map<String, Object>> found = rows("select r.assignment_id from employee_assignments r join assignment_versions v on v.assignment_id = r.assignment_id"
                + " where v.change_command_id = ? and r.assignment_kind = 'CONCURRENT'", command);
        assertThat(found).as("START_CONCURRENT " + command + " created a CONCURRENT root with its first version").hasSize(1);
        return fromStored(found.get(0).get("assignment_id"));
    }

    /** The current (not superseded) versions of the root, oldest first. */
    protected List<Map<String, Object>> versionsOfRoot(UUID root) throws Exception {
        return rows("select * from assignment_versions where assignment_id = ? and superseded_at is null order by effective_from_date, revision_number", root);
    }

    /** The relationship id of the version of the root in force on the day, null when none covers it. */
    protected UUID relationshipOfRootOn(UUID root, LocalDate day) throws Exception {
        Map<String, Object> version = inForceOn(versionsOfRoot(root), day);
        return version == null ? null : fromStored(version.get("employment_relationship_id"));
    }

    /** The entry is refused for its content (a DomainException) and leaves no command row of the person. */
    protected Throwable refusedWithoutRow(UUID person, Runnable entry, String why) throws Exception {
        long before = commandCount(person);
        Throwable refusal = catchThrowable(entry::run);
        assertThat(refusal).as(why).isInstanceOf(DomainException.class);
        assertThat(commandCount(person)).as(why + ": a refused command leaves no row").isEqualTo(before);
        return refusal;
    }

    /** As {@link #refusedWithoutRow}, and the refusal is a conflict (the design names no error code for the same-day rule of 5). */
    protected void refusedAsConflict(UUID person, Runnable entry, String why) throws Exception {
        Throwable refusal = refusedWithoutRow(person, entry, why);
        assertThat(((DomainException) refusal).kind()).as(why + ": a conflict").isEqualTo(ErrorKind.CONFLICT);
    }
}
