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
 * #1371 acceptance part, database lines of done-when 2 of docs/architecture/proposals/employment-relationship.md (section 4.1 and 4.2):
 * one relationship per person and employer, versions of one relationship never overlap while two relationships of one person may, the
 * per-kind CHECK on the relationship id of personnel commands, the immutable employer (4.2-2) and the predecessor link. Rows are written
 * directly, as a writer that bypassed the application would, so only the database is under test.
 */
class EmploymentRelationshipSchemaIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipSchemaIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";
    private static final String EXCLUSION_VIOLATION = "23P01";

    private OffsetDateTime at;
    private UUID person;
    private UUID employerA;
    private UUID employerB;

    @FunctionalInterface
    private interface Statement {
        void run() throws Exception;
    }

    @BeforeEach
    void aPersonWithoutRelationships() throws Exception {
        at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            assertThat(query(workforce, "select 1 as present from information_schema.tables where table_schema = 'organization_workforce' and table_name = 'employment_relationships'"))
                    .as("design 4.1: the table employment_relationships exists").isNotEmpty();
            assertThat(query(workforce, "select 1 as present from information_schema.tables where table_schema = 'organization_workforce' and table_name = 'employment_relationship_versions'"))
                    .as("design 4.1 and ruling 4: employee_versions is renamed employment_relationship_versions").isNotEmpty();
        }
        person = newEmployeeId();
        employee(person, unit, null);
        employerA = legalEntity;
        employerB = createLegalEntity(administrator, newLegalEntityCode("ER"), "Synthetic second employer");
    }

    private UUID relationship(UUID employee, UUID employer, UUID predecessor) throws SQLException {
        return relationship(Uuids.timeOrdered(NOW), employee, employer, predecessor);
    }

    private UUID relationship(UUID id, UUID employee, UUID employer, UUID predecessor) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employment_relationships (employment_relationship_id, employee_id, employer_legal_entity_id, predecessor_relationship_id,
                      employer_source_kind, created_at, updated_at) values (?, ?, ?, ?, 'DECLARED', ?, ?)""", id, stored(employee), stored(employer), predecessor, at, at);
        }
        return id;
    }

    /** An open version of the relationship from the day. */
    private void version(UUID employee, UUID relationship, LocalDate from, int revision) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employment_relationship_versions (employment_relationship_version_id, employee_id, employment_relationship_id, effective_from_date,
                      revision_number, employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date,
                      employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, ?, ?, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(employee), relationship, from, revision, from, at, at);
        }
    }

    private void command(UUID employee, UUID relationship, String kind) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into personnel_commands (personnel_command_id, employee_id, employment_relationship_id, command_kind, effective_date, status,
                      created_at, created_by, updated_at)
                    values (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(employee), relationship, kind, TODAY.plusDays(3), at, hr.userId(), at);
        }
    }

    private static void refusedBy(Statement statement, String sqlState, String why) {
        SQLException refused = catchThrowableOfType(SQLException.class, statement::run);
        assertThat((Throwable) refused).as("refused: " + why).isNotNull();
        if (sqlState != null) {
            assertThat(refused.getSQLState()).as(why + ": " + refused.getMessage()).isEqualTo(sqlState);
        }
    }

    @Test
    @DisplayName("#1371 完成标准 2（设计 4.1）：同一人与同一用人单位只能有一段雇佣关系，同一人与另一用人单位可再有一段")
    void oneRelationshipPerPersonAndEmployer() throws Exception {
        relationship(person, employerA, null);

        refusedBy(() -> relationship(person, employerA, null), UNIQUE_VIOLATION, "design 4.1 UNIQUE (employee_id, employer_legal_entity_id)");
        relationship(person, employerB, null);

        assertThat(rows("select 1 from employment_relationships where employee_id = ?", stored(person)))
                .as("design 4.2-1: two employers, two relationships of one person").hasSize(2);
    }

    @Test
    @DisplayName("#1371 完成标准 2（设计 4.1）：同一段关系的有效版本不得重叠；同一人两段关系的版本可以重叠")
    void versionsOverlapAcrossRelationshipsButNotWithin() throws Exception {
        UUID first = relationship(person, employerA, null);
        UUID second = relationship(person, employerB, null);

        version(person, first, TODAY.minusDays(30), 0);
        version(person, second, TODAY.minusDays(10), 0);

        refusedBy(() -> version(person, first, TODAY.minusDays(5), 1), EXCLUSION_VIOLATION, "design 4.1 exclusion per relationship");
        assertThat(rows("select 1 from employment_relationship_versions where employee_id = ? and effective_to_date is null and superseded_at is null", stored(person)))
                .as("design 4.2-1: the two relationships are open together").hasSize(2);
    }

    @Test
    @DisplayName("#1371 完成标准 2（设计 4.1）：命令的关系编号按命令种类约束：入职缺关系编号被拒，变更法人（人员层）可以为空")
    void theRelationshipIdOfACommandFollowsItsKind() throws Exception {
        relationship(person, employerA, null);

        refusedBy(() -> command(person, null, "HIRE"), CHECK_VIOLATION, "design 4.1 per-kind CHECK: HIRE needs a relationship id");
        refusedBy(() -> command(person, null, "TRANSFER"), CHECK_VIOLATION, "design 4.1 per-kind CHECK: TRANSFER needs a relationship id");
        command(person, null, "CHANGE_LEGAL_ENTITY");

        assertThat(rows("select 1 from personnel_commands where employee_id = ? and command_kind = 'CHANGE_LEGAL_ENTITY' and employment_relationship_id is null",
                stored(person))).as("design 4.1: a person-level command carries no relationship id").hasSize(1);
    }

    @Test
    @DisplayName("#1371 完成标准 2（设计 4.1）：命令的关系必须属于该员工（复合外键）")
    void aCommandCannotNameAnotherPersonsRelationship() throws Exception {
        UUID other = newEmployeeId();
        employee(other, unit, null);
        UUID othersRelationship = relationship(other, employerA, null);

        refusedBy(() -> command(person, othersRelationship, "TRANSFER"), FOREIGN_KEY_VIOLATION, "design 4.1 composite FK (relationship, employee)");
    }

    @Test
    @DisplayName("#1371 设计 4.2-2：一段关系的用人单位不得被改写")
    void theEmployerNeverChanges() throws Exception {
        UUID id = relationship(person, employerA, null);

        refusedBy(() -> {
            try (Connection workforce = schemaConnection("organization_workforce")) {
                update(workforce, "update employment_relationships set employer_legal_entity_id = ? where employment_relationship_id = ?", stored(employerB), id);
            }
        }, null, "design 4.2-2 employer immutability");
    }

    @Test
    @DisplayName("#1371 设计 4.1：前任关系不得指向自己，也不得指向别人的关系")
    void thePredecessorIsAnotherRelationshipOfTheSamePerson() throws Exception {
        UUID first = relationship(person, employerA, null);
        UUID other = newEmployeeId();
        employee(other, unit, null);
        UUID othersRelationship = relationship(other, employerA, null);
        UUID self = Uuids.timeOrdered(NOW);

        refusedBy(() -> relationship(self, person, employerB, self), CHECK_VIOLATION, "design 4.1 CHECK predecessor <> self");
        refusedBy(() -> relationship(person, employerB, othersRelationship), FOREIGN_KEY_VIOLATION, "design 4.1 composite predecessor FK: same employee");
        relationship(person, employerB, first);
    }
}
