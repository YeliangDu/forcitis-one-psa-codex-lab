package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.DONE;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1371 acceptance part, done-when 3 (rest) of the employment-relationship design (ADR-0086) (section 7, MigrateInitialVersions): the operations
 * command writes the first employee versions on the relationship a person already has, creates an unconfirmed MIGRATION_ATTRIBUTION
 * relationship for a person with no row anywhere (employer = the attribution entity on the snapshot day), and does not guess for a person without
 * attribution. It is driven as the existing InitialVersionMigrationIT drives it; the first two tests also assert that the run succeeded; the
 * third does not, because design 7 names no exit code for a person who is refused and listed.
 */
class EmploymentRelationshipMigrateVersionsIT extends InitialVersionMigrationSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipMigrateVersionsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private List<Map<String, Object>> relationshipsOf(UUID person) throws Exception {
        return rows("select * from employment_relationships where employee_id = ? order by employment_relationship_id", stored(person));
    }

    @Test
    @DisplayName("#1371 完成标准 3 余项（设计 7）：有任职版本、其已有关系上没有员工版本的人：首批员工版本写在他已有的那段关系上，不新建关系、不改关系行")
    void theFirstVersionsOfAPersonWithAssignmentVersionsGoOnHisExistingRelationship() throws Exception {
        UUID person = snapshotPerson(false, null);
        syncAssignmentVersion(person, LocalDate.of(2026, 3, 1), null);
        List<Map<String, Object>> relationships = relationshipsOf(person);
        assertThat(relationships).as("set-up: the assignment version hangs on exactly one existing relationship").hasSize(1);
        assertThat(currentVersions(EMPLOYEE_VERSIONS, person)).as("set-up: no employee version yet").isEmpty();

        Result result = migrate();

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        List<Map<String, Object>> versions = currentVersions(EMPLOYEE_VERSIONS, person);
        assertThat(versions).as("design 7: the migration wrote the first employee version").isNotEmpty();
        assertThat(versions).as("design 7: every one is on the existing relationship").allSatisfy(version ->
                assertThat(version.get("employment_relationship_id")).as("relationship of the version").isEqualTo(relationships.get(0).get("employment_relationship_id")));
        assertThat(relationshipsOf(person)).as("design 7: no relationship was created and the existing row is unchanged").isEqualTo(relationships);
    }

    @Test
    @DisplayName("#1371 完成标准 3 余项（设计 7）：哪里都没有行、有法人归属的人：建一段未确认的 MIGRATION_ATTRIBUTION 关系，用人单位是快照日的归属法人，版本挂在其上")
    void aPersonWithNoRowAnywhereGetsAnUnconfirmedAttributionRelationship() throws Exception {
        UUID person = snapshotPerson(false, null);
        attributeEmployee(administrator, person, legalEntity);
        assertThat(relationshipsOf(person)).as("set-up: nothing anywhere yet").isEmpty();

        Result result = migrate();

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        List<Map<String, Object>> relationships = relationshipsOf(person);
        assertThat(relationships).as("design 7: exactly one relationship was created").hasSize(1);
        Map<String, Object> relationship = relationships.get(0);
        assertThat(relationship.get("employer_source_kind")).as("design 7: the employer is an attribution, not a declaration").isEqualTo("MIGRATION_ATTRIBUTION");
        assertThat(relationship.get("employer_confirmed_at")).as("design 7: awaiting HR confirmation").isNull();
        assertThat(fromStored(relationship.get("employer_legal_entity_id"))).as("design 7: the attribution entity on the snapshot day").isEqualTo(legalEntity);
        assertThat(currentVersions(EMPLOYEE_VERSIONS, person)).as("design 7: the first versions are written and all hang on the new relationship").isNotEmpty()
                .allSatisfy(version -> assertThat(version.get("employment_relationship_id")).as("relationship of the version").isEqualTo(relationship.get("employment_relationship_id")));
        assertThat(currentVersions(ASSIGNMENT_VERSIONS, person)).as("design 7: so does the backfilled assignment version").isNotEmpty()
                .allSatisfy(version -> assertThat(version.get("employment_relationship_id")).as("relationship of the assignment version").isEqualTo(relationship.get("employment_relationship_id")));
    }

    @Test
    @DisplayName("#1371 完成标准 3 余项（设计 7）：没有法人归属的人不被猜测：不建关系、不写任何版本，命令输出里没有此人的编号；别的人已有的关系不受影响")
    void aPersonWithoutAttributionIsRefusedAndNothingIsWrittenForHim() throws Exception {
        UUID person = snapshotPerson(false, null);
        UUID other = snapshotPerson(false, null);
        syncAssignmentVersion(other, LocalDate.of(2026, 3, 1), null);
        List<Map<String, Object>> otherBefore = relationshipsOf(other);
        assertThat(otherBefore).as("set-up: the other person has one existing relationship").hasSize(1);
        assertThat(relationshipsOf(person)).as("set-up: the person has nothing anywhere").isEmpty();

        Result result = migrateExcept(employed(REGULAR), employed(REGULAR), person);

        assertThat(relationshipsOf(person)).as("design 7: no employer is guessed, so no relationship").isEmpty();
        assertThat(currentVersions(EMPLOYEE_VERSIONS, person)).as("design 7: it never writes a version without a relationship").isEmpty();
        assertThat(currentVersions(ASSIGNMENT_VERSIONS, person)).as("and no assignment version either").isEmpty();
        assertThat(relationshipsOf(other)).as("the other person's existing relationship row is unchanged").isEqualTo(otherBefore);
        assertThat(result.text()).as("design 7: the reject list is an aggregate count, the person's id is personal data").doesNotContain(person.toString());
    }
}
