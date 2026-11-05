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
 * #1371 acceptance part, done-when 9 of the employment-relationship design (ADR-0086) (section 6.2): the person-level availability
 * read composes over the relationships in service: onUnpaidLeave only if all are, leaving only if all have an effective separation ahead,
 * separationHeld if any has a held separation. The existing read {@code OrgDirectory.availabilityOn} is used; the second relationship
 * is made by the HIRE input of the lead ruling on #1371.
 */
class EmploymentRelationshipCompositionIT extends EmploymentRelationshipSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipCompositionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private UUID person;
    private UUID relationA;
    private UUID relationB;

    @BeforeEach
    void aPersonEmployedByTwoEmployers() throws Exception {
        UUID employerB = otherEmployer();
        person = personOf(hireNewPerson(legalEntity));
        hireAdditionalEmployer(person, employerB, TODAY, null);
        relationA = relationshipOf(person, legalEntity);
        relationB = relationshipOf(person, employerB);
        assertThat(relationB).as("design 5: two relationships in service").isNotNull();
    }

    @Test
    @DisplayName("#1371 完成标准 9（设计 6.2）：停薪留职——只有一段在停薪留职时人不算，两段都在时才算")
    void unpaidLeaveNeedsAllRelationships() {
        startUnpaidLeave(person, relationA, TODAY);

        assertThat(availability(person, TODAY).onUnpaidLeave()).as("design 6.2: one of two is on unpaid leave, the person is not").isFalse();

        startUnpaidLeave(person, relationB, TODAY);

        assertThat(availability(person, TODAY).onUnpaidLeave()).as("design 6.2: all in-service relationships are on unpaid leave").isTrue();
    }

    @Test
    @DisplayName("#1371 完成标准 9（设计 6.2）：即将离职——只有一段有生效的离职在前时人不算，两段都有时才算")
    void leavingNeedsAllRelationships() {
        LocalDate lastWorkingDay = TODAY.plusDays(20);
        endRelationship(person, relationB, lastWorkingDay);

        assertThat(availability(person, TODAY).leaving()).as("design 6.2: one of two has a separation ahead, the person is not leaving").isFalse();

        endRelationship(person, relationA, lastWorkingDay);

        assertThat(availability(person, TODAY).leaving()).as("design 6.2: every in-service relationship has a separation ahead").isTrue();
    }

    @Test
    @DisplayName("#1371 完成标准 9（设计 6.2）：暂停离职——任一段有被暂停的离职，人就算被暂停")
    void separationHeldNeedsAnyRelationship() throws Exception {
        Staff leaver = administrator();
        administrator();
        auditor();
        UUID employerB = otherEmployer();
        hireAdditionalEmployer(leaver.employeeId(), employerB, TODAY, null);
        UUID own = relationshipOf(leaver.employeeId(), legalEntity);
        UUID extra = relationshipOf(leaver.employeeId(), employerB);
        assertThat(availability(leaver.employeeId(), TODAY).separationHeld()).as("nothing is held yet").isFalse();

        actIn(hr);
        UUID command = endRelationship(leaver.employeeId(), extra, TODAY.plusDays(9));

        assertThat(commandStatus(command)).as("the separation of an administrator relationship is held").isEqualTo(PersonnelCommandNames.HELD);
        assertThat(availability(leaver.employeeId(), TODAY).separationHeld()).as("design 6.2: one of two relationships has a held separation, so the person has").isTrue();
        assertThat(own).as("the held one is not the other relationship").isNotEqualTo(extra);
    }
}
