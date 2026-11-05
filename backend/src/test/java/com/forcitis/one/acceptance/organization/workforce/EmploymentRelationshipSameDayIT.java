package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1371 acceptance part, done-when 12a of the employment-relationship design (ADR-0086) (sections 4.1 and 5): at most one open command per
 * employee and effective day produces a primary assignment version, checked by the application under the root lock. The design names no error
 * code for it, so the refusal is asserted as a DomainException of kind CONFLICT and as leaving no command row. The accepted case
 * (SEPARATE old + HIRE with predecessor on one day) is in EmploymentRelationshipLifecycleIT (done-when 6 and 12).
 */
class EmploymentRelationshipSameDayIT extends EmploymentRelationshipBindingSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipSameDayIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate DAY = TODAY.plusDays(5);

    private UUID person;
    private UUID employerB;
    private UUID employerC;

    @BeforeEach
    void aPersonInServiceAndTwoOtherEmployers() throws Exception {
        employerB = otherEmployer();
        employerC = otherEmployer();
        person = personOf(hireNewPerson(legalEntity));
        actIn(hr);
        assertThat(relationshipOf(person, legalEntity)).as("design 5: the first HIRE created the person's relationship").isNotNull();
    }

    @Test
    @DisplayName("#1371 完成标准 12a：同一员工同一生效日，带主任职的 HIRE(B) 之后再录主任职上的 TRANSFER 被拒（冲突），不留命令行")
    void aTransferOfThePrimaryAfterAHireWithAPostIsRefused() throws Exception {
        UUID hire = hireWithPost(person, employerB, DAY, newUnit(), newPosition());
        assertThat(commandStatus(hire)).as("design 4.1: the HIRE carrying both target columns is accepted and waits for its day").isEqualTo(PersonnelCommandNames.PENDING);

        UUID toUnit = newUnit();
        refusedAsConflict(person, () -> transfer(person, DAY, toUnit), "design 12a: HIRE(B) with a post and a TRANSFER of the primary on one day both produce a primary version");

        assertThat(commandStatus(hire)).as("design 12a: the first command stays as it was").isEqualTo(PersonnelCommandNames.PENDING);
    }

    @Test
    @DisplayName("#1371 完成标准 12a：同一员工同一生效日，先录主任职上的 TRANSFER 再录带主任职的 HIRE(B) 同样被拒（冲突）")
    void aHireWithAPostAfterATransferOfThePrimaryIsRefused() throws Exception {
        UUID move = transfer(person, DAY, newUnit());
        assertThat(commandStatus(move)).as("the TRANSFER of the primary is accepted and waits for its day").isEqualTo(PersonnelCommandNames.PENDING);
        UUID postUnit = newUnit();
        UUID post = newPosition();

        refusedAsConflict(person, () -> hireWithPost(person, employerB, DAY, postUnit, post),
                "design 12a: the order of the two entries does not matter, one primary-producing command per employee and day");

        assertThat(commandStatus(move)).as("design 12a: the first command stays as it was").isEqualTo(PersonnelCommandNames.PENDING);
    }

    @Test
    @DisplayName("#1371 完成标准 12a：同一员工同一生效日，带主任职的 HIRE(B) 与带主任职的 REINSTATE(C) 被拒（冲突）")
    void aHireWithAPostAndAReinstatementOnOneDayAreRefused() throws Exception {
        UUID subject = employed(PersonnelCommandNames.REGULAR);
        actIn(hr);
        hireAdditionalEmployer(subject, employerC, TODAY.minusDays(20), null);
        UUID ended = relationshipOf(subject, employerC);
        endRelationship(subject, ended, TODAY.minusDays(10));
        assertThat(statusOf(subject)).as("design 4.2-3: the world's relationship keeps the person in service after C ended").isEqualTo(PersonnelCommandNames.EMPLOYED);
        UUID hire = hireWithPost(subject, employerB, DAY, newUnit(), newPosition());
        assertThat(commandStatus(hire)).as("the HIRE carrying a post is accepted").isEqualTo(PersonnelCommandNames.PENDING);
        UUID unit = newUnit();
        UUID position = newPosition();

        refusedAsConflict(subject, () -> reinstateIn(subject, ended, DAY, unit, position),
                "design 12a: HIRE(B) and REINSTATE(C) with both target columns set both produce a primary version on one day");

        assertThat(commandStatus(hire)).as("design 12a: the first command stays as it was").isEqualTo(PersonnelCommandNames.PENDING);
    }
}
