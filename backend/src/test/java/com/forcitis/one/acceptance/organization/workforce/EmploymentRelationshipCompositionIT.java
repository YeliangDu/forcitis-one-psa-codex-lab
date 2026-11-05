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
 * read composes over the relationships in service: onUnpaidLeave only if all are, leaving only if every in-service relationship is leaving (a held SEPARATE or a pending UNPAID_LEAVE_START; a plain SEPARATE without a hold is not),
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
    @DisplayName("#1371 完成标准 9（设计 6.2）：即将离职——只有一段有被暂停的离职时人不算，两段都有时才算")
    void leavingNeedsAllRelationships() throws Exception {
        Staff leaver = administrator();
        administrator();
        auditor();
        UUID employerB = otherEmployer();
        hireAdditionalEmployer(leaver.employeeId(), employerB, TODAY, null);
        UUID own = relationshipOf(leaver.employeeId(), legalEntity);
        UUID extra = relationshipOf(leaver.employeeId(), employerB);
        assertThat(own).as("the administrator has a relationship with the world's entity").isNotNull();
        assertThat(extra).as("and a second one").isNotNull().isNotEqualTo(own);
        actIn(hr);

        // design 6.3: the relationship holding the primary post ends last, so the extra one is separated first.
        UUID first = endRelationship(leaver.employeeId(), extra, TODAY.plusDays(9));
        assertThat(commandStatus(first)).as("the separation of an administrator relationship is held").isEqualTo(PersonnelCommandNames.HELD);
        assertThat(availability(leaver.employeeId(), TODAY).leaving())
                .as("design 6.2: one of two relationships carries a held separation, the other is working, so the person is not leaving").isFalse();

        UUID second = endRelationship(leaver.employeeId(), own, TODAY.plusDays(12));
        assertThat(commandStatus(second)).as("the second separation is held as well").isEqualTo(PersonnelCommandNames.HELD);
        assertThat(availability(leaver.employeeId(), TODAY).leaving()).as("design 6.2: every in-service relationship carries a held separation").isTrue();
    }

    @Test
    @DisplayName("#1371 完成标准 9（设计 6.2）：没有暂停的普通离职不算即将离职，两段都有也不算")
    void aPlainSeparationWithoutAHoldIsNotLeaving() throws Exception {
        UUID first = endRelationship(person, relationB, TODAY.plusDays(9));
        UUID second = endRelationship(person, relationA, TODAY.plusDays(12));

        assertThat(commandStatus(first)).as("a non-administrator's separation is not held").isEqualTo(PersonnelCommandNames.PENDING);
        assertThat(commandStatus(second)).as("neither is the second").isEqualTo(PersonnelCommandNames.PENDING);
        assertThat(availability(person, TODAY).leaving()).as("design 6.2 and done-when 9: a plain SEPARATE without a hold is not leaving, on every relationship either").isFalse();
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
        assertThat(own).as("the administrator has a relationship with the world's entity, not the held one").isNotNull().isNotEqualTo(extra);
    }
}
