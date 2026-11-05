package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1371 acceptance part, done-when 14 of the employment-relationship design (ADR-0086) (section 4.1, business key of the version table):
 * revision numbers are counted per relationship, so two relationships of one person each hold revision 0 on the same day, while one
 * relationship holds a given revision on a given day once. Rows are written directly, so only the database is under test.
 */
class EmploymentRelationshipRevisionIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipRevisionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String REVISION_KEY = "uk_employment_relationship_versions_revision";
    private static final LocalDate DAY = TODAY.minusDays(10);

    private OffsetDateTime at;
    private UUID person;
    private UUID first;
    private UUID second;

    @BeforeEach
    void aPersonWithTwoRelationships() throws Exception {
        at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        person = newEmployeeId();
        employee(person, unit, null);
        first = relationship(legalEntity);
        second = relationship(createLegalEntity(administrator, newLegalEntityCode("ER"), "Synthetic second employer"));
        assertThat(second).as("design 4.1: two employers, two relationships of one person").isNotEqualTo(first);
    }

    private UUID relationship(UUID employer) throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employment_relationships (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind, created_at, updated_at)
                    values (?, ?, ?, 'DECLARED', ?, ?)""", id, stored(person), stored(employer), at, at);
        }
        return id;
    }

    /**
     * A version of the relationship from {@code DAY}. An {@code empty} version ends the day it starts (the range is empty), so it takes part in
     * no overlap exclusion and only the business key can refuse it.
     */
    private void version(UUID relationship, int revision, boolean empty) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employment_relationship_versions (employment_relationship_version_id, employee_id, employment_relationship_id, effective_from_date,
                      effective_to_date, revision_number, employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date,
                      employment_start_basis_kind, change_channel_kind, closed_at, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(person), relationship, DAY, empty ? DAY : null, revision, DAY, empty ? at : null, at, at);
        }
    }

    @Test
    @DisplayName("#1371 完成标准 14（设计 4.1）：同一人的两段关系在同一天各有修订号 0，两条都被接受")
    void twoRelationshipsOfOnePersonEachHoldRevisionZeroOnTheSameDay() throws Exception {
        version(first, 0, false);
        version(second, 0, false);

        assertThat(rows("select 1 from employment_relationship_versions where employee_id = ? and effective_from_date = ? and revision_number = 0", stored(person), DAY))
                .as("design 4.1 and done-when 14: revision numbers are per relationship, so both rows exist").hasSize(2);
    }

    @Test
    @DisplayName("#1371 完成标准 14（设计 4.1）：同一段关系同一天的修订号 0 不得出现第二次（唯一约束 uk_employment_relationship_versions_revision），换成修订号 1 则被接受")
    void oneRelationshipHoldsARevisionOnADayOnce() throws Exception {
        version(first, 0, false);

        SQLException refused = catchThrowableOfType(SQLException.class, () -> version(first, 0, true));

        assertThat((Throwable) refused).as("design 4.1: a second revision 0 of the same relationship and day is refused").isNotNull();
        assertThat(refused.getSQLState()).as("a unique violation: " + refused.getMessage()).isEqualTo(UNIQUE_VIOLATION);
        assertThat(refused.getMessage()).as("refused by the per-relationship business key").contains("\"" + REVISION_KEY + "\"");
        version(first, 1, true);
        assertThat(rows("select 1 from employment_relationship_versions where employment_relationship_id = ? and effective_from_date = ?", first, DAY))
                .as("design 4.1 control: the next revision of the same relationship and day is accepted").hasSize(2);
    }
}
