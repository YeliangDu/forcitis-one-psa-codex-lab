package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1371 acceptance part, done-when 3 (rest) of the employment-relationship design (ADR-0086) (sections 6.2 and 7): the dry run of
 * MigrateInitialVersions writes nothing; the read api returns
 * {@code employerConfirmed} false for a backfilled row, true after the one confirmation and for DECLARED rows, and employee-level reads do not
 * depend on it. The design does not name the dry-run option: it is proposed as {@code --dry-run} (listed in the card report as a name to rule). The
 * reject list has no read path and its count is not tested (lead ruling).
 */
class EmploymentRelationshipMigrateDryRunIT extends InitialVersionMigrationSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipMigrateDryRunIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** Proposed name of the dry-run option (design 7, "dry-run mode"). */
    private static final String DRY_RUN_OPTION = "--dry-run";

    @Autowired
    private ApplicationContext beans;

    private List<Long> tableCounts() throws Exception {
        return List.of(
                (long) rows("select count(*) as n from employment_relationships").get(0).get("n"),
                (long) rows("select count(*) as n from employment_relationship_versions").get(0).get("n"),
                (long) rows("select count(*) as n from assignment_versions").get(0).get("n"),
                (long) rows("select count(*) as n from personnel_commands").get(0).get("n"));
    }

    @Test
    @DisplayName("#1371 完成标准 3 余项（设计 7）：试运行什么都不写（关系、版本、任职版本、命令、事件），输出里没有任何人的编号")
    void theDryRunWritesNothing() throws Exception {
        UUID attributed = snapshotPerson(false, null);
        attributeEmployee(administrator, attributed, legalEntity);
        UUID withAssignments = snapshotPerson(false, null);
        syncAssignmentVersion(withAssignments, LocalDate.of(2026, 3, 1), null);
        UUID rejected = snapshotPerson(false, null);
        UUID operator = employed(REGULAR);
        UUID witness = employed(REGULAR);
        List<Long> before = tableCounts();
        List<Map<String, Object>> versionsBefore = everyVersionRow();
        long events = publishedEvents();

        Result result = run("--" + OPERATION_OPTION + "=" + MIGRATE_OPERATION, "--" + OPERATOR_OPTION + "=" + operator, "--" + WITNESS_OPTION + "=" + witness, DRY_RUN_OPTION);

        assertThat(result.exitCode()).as("design 7: the dry run is a successful run. " + result.text()).isEqualTo(DONE);
        assertThat(tableCounts()).as("design 7: the dry run writes no relationship, version, assignment version or command").isEqualTo(before);
        assertThat(everyVersionRow()).as("design 7: and no version row changed").isEqualTo(versionsBefore);
        assertThat(publishedEvents()).as("design 7 and done-when 10: the migration publishes no event, the dry run least of all").isEqualTo(events);
        assertThat(result.text()).as("design 7: no person's id in the command output").doesNotContain(attributed.toString(), withAssignments.toString(), rejected.toString());
    }

    @Test
    @DisplayName("#1371 完成标准 3 余项（设计 6.2、7）：读口 employerConfirmed：迁入回填行为 false，确认后为 true，DECLARED 行为 true；员工层的在职不受它影响")
    void employerConfirmedFollowsTheSourceAndTheConfirmation() throws Exception {
        UUID migrated = snapshotPerson(false, null);
        attributeEmployee(administrator, migrated, legalEntity);
        UUID declared = employed(REGULAR);

        Result result = migrate();

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        assertThat(EmploymentRelationshipReads.employerConfirmed(beans, employeeIdOf(migrated), TODAY, legalEntity))
                .as("design 6.2 and 7: a backfilled row is not confirmed").isFalse();
        assertThat(EmploymentRelationshipReads.employerConfirmed(beans, employeeIdOf(declared), TODAY, legalEntity))
                .as("design 6.2: a DECLARED row is confirmed").isTrue();
        assertThat(rows("select employment_status from employees where id = ?", stored(migrated)).get(0).get("employment_status"))
                .as("design 7: employee-level reads are unaffected by an unconfirmed employer").isEqualTo(EMPLOYED);

        confirm(migrated);

        assertThat(EmploymentRelationshipReads.employerConfirmed(beans, employeeIdOf(migrated), TODAY, legalEntity))
                .as("design 6.2 and 7: true after the one confirmation").isTrue();
        assertThat(rows("select employment_status from employees where id = ?", stored(migrated)).get(0).get("employment_status"))
                .as("design 7: and employee-level reads are still the same").isEqualTo(EMPLOYED);
    }

    /** HR's confirmation: the one allowed update of a migrated row (design 4.1 and 7); the way HR does it is not part of this card, so it is done in SQL. */
    private void confirm(UUID person) throws Exception {
        try (java.sql.Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employment_relationships set employer_confirmed_at = now() where employee_id = ? and employer_source_kind = 'MIGRATION_ATTRIBUTION'", stored(person));
        }
    }
}
