package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
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
 * #1371 acceptance part, done-when 13 of the employment-relationship design (ADR-0086) (sections 4.1, 4.2-7, 4.2-8 and 5): a command names the
 * relationship of the assignment version it produces; only a PRIMARY TRANSFER may move the primary to another relationship, and then that
 * relationship must be in service on the day; SEPARATE closes only the assignment versions of the separated relationship. The design names no
 * error code for these refusals, so they are asserted as a DomainException that leaves no command row.
 */
class EmploymentRelationshipBindingIT extends EmploymentRelationshipBindingSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipBindingIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

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
        assertThat(relationB).as("design 5: the second employer's HIRE created a relationship").isNotNull().isNotEqualTo(relationA);
        assertThat(relationshipOfRootOn(primaryRootOf(person), TODAY)).as("design 4.1: the PRIMARY post is held by the first relationship").isEqualTo(relationA);
    }

    @Test
    @DisplayName("#1371 完成标准 13（设计 4.2-8）：兼职根留在建它的关系里，TRANSFER 兼职根却写另一段关系被拒；写本段关系的同一命令被接受")
    void aConcurrentPostCannotBeTransferredIntoAnotherRelationship() throws Exception {
        UUID start = startConcurrentIn(person, relationB, TODAY, newUnit(), newPosition());
        UUID post = rootCreatedBy(start);
        assertThat(relationshipOfRootOn(post, TODAY)).as("design 4.1: the START_CONCURRENT version carries the relationship the command named").isEqualTo(relationB);
        UUID toUnit = newUnit();

        refusedWithoutRow(person, () -> transferIn(person, relationA, TODAY.plusDays(3), post, toUnit),
                "design 4.2-8: the command names A, the version it produces on a root of B would carry A");
        UUID own = transferIn(person, relationB, TODAY.plusDays(3), post, toUnit);

        assertThat(commandStatus(own)).as("design 4.2-8 control: naming the relationship of the root is accepted").isEqualTo(PersonnelCommandNames.PENDING);
        assertThat(relationshipOfRootOn(post, TODAY)).as("design 4.2-8: the refused command changed nothing on the post").isEqualTo(relationB);
    }

    @Test
    @DisplayName("#1371 完成标准 13（设计 4.2-8、6.3）：主任职上的 TRANSFER 写当日在职的另一段关系被接受，主任职在同一根上换到该关系，旧版本无缝关闭")
    void aPrimaryTransferMovesThePrimaryToARelationshipInService() throws Exception {
        UUID root = primaryRootOf(person);
        UUID toUnit = newUnit();
        LocalDate day = TODAY.plusDays(3);

        UUID move = transferIn(person, relationB, day, null, toUnit);
        dueOn(day);

        assertThat(commandStatus(move)).as("design 4.2-8: the exception is accepted and took effect").isEqualTo(PersonnelCommandNames.EFFECTIVE);
        assertThat(relationshipOfRootOn(root, day.minusDays(1))).as("design 4.1: the day before, the primary was still in A").isEqualTo(relationA);
        assertThat(relationshipOfRootOn(root, day)).as("design 6.3: from the day the same root carries B").isEqualTo(relationB);
        List<Map<String, Object>> versions = versionsOfRoot(root);
        assertThat(versions.get(versions.size() - 1).get("effective_to_date")).as("the new version is open").isNull();
        assertThat(inForceOn(versions, day.minusDays(1)).get("effective_to_date")).as("design 6.3: the old version closes on the day, no gap").isEqualTo(day);
        assertThat(rows("select 1 from employee_assignments where employee_id = ? and assignment_kind = 'PRIMARY'", stored(person)))
                .as("design 4.1: still one PRIMARY root").hasSize(1);
    }

    @Test
    @DisplayName("#1371 完成标准 13（设计 4.2-8）：主任职上的 TRANSFER 写当日尚未在职的关系被拒，该关系在职的那天同一命令被接受")
    void aPrimaryTransferToARelationshipNotInServiceThatDayIsRefused() throws Exception {
        UUID later = otherEmployer();
        LocalDate starts = TODAY.plusDays(10);
        hireAdditionalEmployer(person, later, starts, null);
        UUID relationLater = relationshipOf(person, later);
        UUID toUnit = newUnit();

        refusedWithoutRow(person, () -> transferIn(person, relationLater, TODAY.plusDays(3), null, toUnit),
                "design 4.2-8: the target relationship must be in service on the effective day, and it starts only on " + starts);
        UUID accepted = transferIn(person, relationLater, starts.plusDays(2), null, toUnit);

        assertThat(commandStatus(accepted)).as("design 4.2-8 control: the same move on a day the relationship is in service is accepted").isEqualTo(PersonnelCommandNames.PENDING);
    }

    @Test
    @DisplayName("#1371 完成标准 13（设计 4.2-7）：SEPARATE 第二段关系只关闭它的任职版本（兼职）；第一段关系的主任职版本与关系版本保持开放，人仍在职")
    void separatingARelationshipClosesOnlyItsOwnAssignmentVersions() throws Exception {
        UUID start = startConcurrentIn(person, relationB, TODAY, newUnit(), newPosition());
        UUID post = rootCreatedBy(start);
        UUID primary = primaryRootOf(person);
        LocalDate day = TODAY.plusDays(5);

        endRelationship(person, relationB, day.minusDays(1));
        dueOn(day);

        Map<String, Object> closed = inForceOn(versionsOfRoot(post), day.minusDays(1));
        assertThat(closed.get("effective_to_date")).as("design 4.2-7: the concurrent post of the separated relationship closes on the separation day").isEqualTo(day);
        assertThat(inForceOn(versionsOfRoot(post), day)).as("and nothing of it is in force afterwards").isNull();
        Map<String, Object> primaryVersion = inForceOn(versionsOfRoot(primary), day);
        assertThat(primaryVersion).as("design 4.2-7: the first relationship's primary version is still in force on the day").isNotNull();
        assertThat(primaryVersion.get("effective_to_date")).as("and still open").isNull();
        assertThat(fromStored(primaryVersion.get("employment_relationship_id"))).as("and still the first relationship's").isEqualTo(relationA);
        assertThat(rows("select 1 from employment_relationship_versions where employment_relationship_id = ? and effective_to_date is null and superseded_at is null", relationA))
                .as("design 4.2-7: the first relationship's own version stays open").hasSize(1);
        assertThat(statusOf(person)).as("design 4.2-3: the person stays in service on the first relationship").isEqualTo(PersonnelCommandNames.EMPLOYED);
    }
}
