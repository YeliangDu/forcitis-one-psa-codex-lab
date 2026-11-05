package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1371 acceptance part, done-when 4, 5, 6 and 8 of the employment-relationship design (ADR-0086) (sections 4.2-3, 5, 6.1, 6.3):
 * a person is in service while any relationship is, the person-level events fire only on person transitions, an employer change in
 * continuous service keeps the person in service, and the relationship that holds the primary post cannot end while another stays.
 * Everything goes through the HIRE and SEPARATE commands and the due-date job; no version is written by hand.
 */
class EmploymentRelationshipLifecycleIT extends EmploymentRelationshipSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipLifecycleIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate FIRST_END = DUE;
    private static final LocalDate LAST_END = DUE.plusDays(4);

    private UUID employerB;
    private UUID person;
    private UUID relationA;
    private UUID relationB;

    @BeforeEach
    void aPersonEmployedByTwoEmployers() throws Exception {
        employerB = otherEmployer();
        person = personOf(hireNewPerson(legalEntity));
        hireAdditionalEmployer(person, employerB, TODAY, null);
        relationA = relationshipOf(person, legalEntity);
        relationB = relationshipOf(person, employerB);
        assertThat(relationA).as("design 5: the first HIRE created a relationship with the world's entity").isNotNull();
        assertThat(relationB).as("design 5: the second HIRE created a relationship with the other employer").isNotNull().isNotEqualTo(relationA);
    }

    @Test
    @DisplayName("#1371 完成标准 4：两段关系结束一段，人仍在职；两段都结束，人离职，EmployeeSeparated 只在最后一段结束时发一次")
    void thePersonLeavesWhenTheLastRelationshipEnds() throws Exception {
        // design 6.3: the relationship holding the PRIMARY assignment (A) may not end first while B stays, so B ends first and A last.
        UUID endB = endRelationship(person, relationB, FIRST_END.minusDays(1));
        UUID endA = endRelationship(person, relationA, LAST_END.minusDays(1));

        dueOn(FIRST_END);

        assertThat(statusOf(person)).as("design 4.2-3: one relationship ended, the other is in service, so the person is").isEqualTo(EMPLOYED);
        assertThat(eventsOf(EVENT_ENDED, endB)).as("design 6.1: the relationship event fires for the first end").isEqualTo(1);
        assertThat(eventsOf(EVENT_SEPARATED, endB)).as("design 6.1: no EmployeeSeparated while a relationship stays in service").isZero();

        dueOn(LAST_END);

        assertThat(statusOf(person)).as("design 4.2-3: both ended, the person is separated").isEqualTo(SEPARATED);
        assertThat(eventsOf(EVENT_ENDED, endA)).as("design 6.1: the relationship event fires for the last end").isEqualTo(1);
        assertThat(eventsOf(EVENT_SEPARATED, endA)).as("design 6.1: EmployeeSeparated fires once, on the last end").isEqualTo(1);
        assertThat(eventsOf(EVENT_SEPARATED, endB)).as("design 6.1: and not on the first end").isZero();
    }

    @Test
    @DisplayName("#1371 完成标准 5：在职者的第二用人单位入职建第二段关系，不动第一段，不发 EmployeeHired")
    void aSecondEmployerHireDoesNotTouchTheFirstRelationship() throws Exception {
        UUID third = otherEmployer();
        List<Map<String, Object>> firstBefore = rows("select * from employment_relationships where employment_relationship_id = ?", relationA);
        List<Map<String, Object>> versionsBefore = rows("select * from employment_relationship_versions where employment_relationship_id = ? order by effective_from_date, revision_number", relationA);

        UUID hire = hireAdditionalEmployer(person, third, TODAY, null);

        assertThat(relationshipsOf(person)).as("design 5: a third relationship exists").hasSize(3);
        assertThat(rows("select * from employment_relationships where employment_relationship_id = ?", relationA)).as("design 5: the first relationship row is unchanged").isEqualTo(firstBefore);
        assertThat(rows("select * from employment_relationship_versions where employment_relationship_id = ? order by effective_from_date, revision_number", relationA))
                .as("design 5: and so are its versions").isEqualTo(versionsBefore);
        assertThat(eventsOf("EmployeeHired", hire)).as("design 6.1: the person was already in service, so no EmployeeHired").isZero();
        assertThat(eventsOf(EVENT_STARTED, hire)).as("design 6.1: the relationship event fires for the second relationship").isEqualTo(1);
    }

    @Test
    @DisplayName("#1371 完成标准 6、12：只有一段关系的人同日 SEPARATE 旧关系 + 带前任的 HIRE 新用人单位：人仍在职，两个人员层事件都不发，主任职在同一根上换到新关系")
    void anEmployerChangeInContinuousServiceKeepsThePersonInService() throws Exception {
        UUID solo = personOf(hireNewPerson(legalEntity));
        UUID old = relationshipOf(solo, legalEntity);
        UUID successorEmployer = otherEmployer();
        UUID newUnit = newUnit();
        UUID newPosition = newPosition();
        UUID primary = (UUID) fromStored(rows("select assignment_id from employee_assignments where employee_id = ? and assignment_kind = 'PRIMARY'", stored(solo)).get(0).get("assignment_id"));
        assertThat(rows("select 1 from assignment_versions where assignment_id = ? and employment_relationship_id = ? and effective_to_date is null", primary, old))
                .as("design 6.3: the only relationship holds the PRIMARY post").hasSize(1);
        LocalDate day = FIRST_END;

        UUID end = endRelationship(solo, old, day.minusDays(1));
        UUID hire = hireSuccessorWithPost(solo, successorEmployer, day, old, newUnit, newPosition);
        dueOn(day);

        UUID successor = relationshipOf(solo, successorEmployer);
        assertThat(successor).as("design 5: HIRE created the relationship with the new employer").isNotNull().isNotEqualTo(old);
        assertThat(rows("select 1 from personnel_commands where personnel_command_id in (?, ?) and status = 'EFFECTIVE'", end, hire))
                .as("design 5: both commands share one date and both took effect").hasSize(2);
        assertThat(statusOf(solo)).as("design 6.1: the person stays in service").isEqualTo(EMPLOYED);
        assertThat(availability(solo, day).leaving()).as("design 4.2-3: someone is in service on the day").isFalse();
        assertThat(eventsOf(EVENT_SEPARATED, end)).as("design 6.1: no EmployeeSeparated on an employer change").isZero();
        assertThat(eventsOf("EmployeeHired", hire)).as("design 6.1: no EmployeeHired on an employer change").isZero();
        assertThat(rows("select 1 from employment_relationships where employment_relationship_id = ? and predecessor_relationship_id = ?", successor, old))
                .as("design 4.1: the new relationship names the one it succeeds").hasSize(1);
        assertThat(rows("select 1 from employee_assignments where employee_id = ? and assignment_kind = 'PRIMARY'", stored(solo))).as("design 4.1: still one PRIMARY root").hasSize(1);
        List<Map<String, Object>> oldVersion = rows("select * from assignment_versions where assignment_id = ? and employment_relationship_id = ? and superseded_at is null", primary, old);
        List<Map<String, Object>> newVersion = rows("select * from assignment_versions where assignment_id = ? and employment_relationship_id = ? and superseded_at is null", primary, successor);
        assertThat(oldVersion).as("design 4.1 and 6.3: the old version of the PRIMARY root stays, carrying the old relationship").hasSize(1);
        assertThat(oldVersion.get(0).get("effective_to_date")).as("design 6.3 and done-when 12: and it is closed on the day, with no gap").isEqualTo(day);
        assertThat(newVersion).as("done-when 12: a new version of the same PRIMARY root carries the new relationship").hasSize(1);
        assertThat(newVersion.get(0).get("effective_to_date")).as("and is open").isNull();
        assertThat(newVersion.get(0).get("effective_from_date")).as("from the day").isEqualTo(day);
        assertThat(fromStored(newVersion.get(0).get("org_unit_id"))).as("with the unit and position of the HIRE").isEqualTo(newUnit);
        assertThat(fromStored(newVersion.get(0).get("position_id"))).isEqualTo(newPosition);
    }

    @Test
    @DisplayName("#1371 完成标准 8：持有主任职的关系在另一段仍在职时不得结束：409、错误码固定、没有任何状态变化")
    void thePrimaryHoldingRelationshipCannotEndWhileAnotherStays() throws Exception {
        Map<String, Long> before = snapshot();
        List<Map<String, Object>> commands = rows("select * from personnel_commands where employee_id = ? order by personnel_command_id", stored(person));

        Throwable refusal = catchThrowable(() -> endRelationship(person, relationA, TODAY.plusDays(20)));

        assertThat(refusal).as("design 6.3: refused").isInstanceOfSatisfying(DomainException.class, domain -> {
            assertThat(domain.errorCode().code()).as(domain.getMessage()).isEqualTo(PRIMARY_ON_ENDING);
            assertThat(domain.kind()).as("conflict").isEqualTo(ErrorKind.CONFLICT);
        });
        assertNothingChanged(before, "design 6.3: the refusal leaves every table as it was");
        assertThat(rows("select * from personnel_commands where employee_id = ? order by personnel_command_id", stored(person))).as("no command row changed").isEqualTo(commands);
    }

    @Test
    @DisplayName("#1371 设计 6.3：不持有主任职的关系可以结束，唯一在职的关系结束时人离职，不需要另指主任职")
    void theOtherRelationshipEndsFreelyAndTheOnlyOneEndsWithThePerson() throws Exception {
        UUID endB = endRelationship(person, relationB, FIRST_END.minusDays(1));
        UUID endA = endRelationship(person, relationA, LAST_END.minusDays(1));

        assertThat(rows("select status from personnel_commands where personnel_command_id in (?, ?)", endB, endA)).as("design 6.3: both are accepted").hasSize(2);
        dueOn(LAST_END);
        assertThat(statusOf(person)).as("design 6.3: the person leaves with the last relationship").isEqualTo(SEPARATED);
    }
}
