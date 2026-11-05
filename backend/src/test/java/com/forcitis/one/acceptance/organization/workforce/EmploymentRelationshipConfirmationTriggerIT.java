package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1371 acceptance part, done-when 15 of the employment-relationship design (ADR-0086) (sections 4.1 and 7): the confirmation trigger. A
 * MIGRATION_ATTRIBUTION relationship accepts exactly one update, {@code employer_confirmed_at} from NULL to a value with every other column
 * unchanged; a second update, an update of the employer, and any update of a DECLARED row are refused. SQL only. The design names no SQLState or
 * trigger name, so the refusal is asserted as SQLState 23001 (restrict_violation, as every append-only trigger of this repository raises) and,
 * as the wording shared with those triggers, "append-only" in the message.
 */
class EmploymentRelationshipConfirmationTriggerIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipConfirmationTriggerIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String RESTRICT_VIOLATION = "23001";

    private OffsetDateTime at;
    private UUID person;
    private UUID otherEmployer;

    @FunctionalInterface
    private interface Statement {
        void run() throws Exception;
    }

    @BeforeEach
    void aPersonAndTwoEmployers() throws Exception {
        at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        person = newEmployeeId();
        employee(person, unit, null);
        otherEmployer = createLegalEntity(administrator, newLegalEntityCode("ER"), "Synthetic other employer");
        try (Connection workforce = schemaConnection("organization_workforce")) {
            assertThat(query(workforce, """
                    select 1 as present from information_schema.triggers where event_object_schema = 'organization_workforce'
                      and event_object_table = 'employment_relationships' and event_manipulation = 'UPDATE' and action_timing = 'BEFORE'"""))
                    .as("design 4.1 and 7: a BEFORE UPDATE trigger guards the relationship table").isNotEmpty();
        }
    }

    private UUID row(UUID employer, String sourceKind) throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employment_relationships (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?)""", id, stored(person), stored(employer), sourceKind, at, at);
        }
        return id;
    }

    private void set(String assignment, UUID id, Object... args) throws SQLException {
        Object[] all = new Object[args.length + 1];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = id;
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employment_relationships set " + assignment + " where employment_relationship_id = ?", all);
        }
    }

    private Map<String, Object> rowOf(UUID id) throws SQLException {
        List<Map<String, Object>> found = rows("select * from employment_relationships where employment_relationship_id = ?", id);
        assertThat(found).as("the relationship row exists").hasSize(1);
        return found.get(0);
    }

    private void refused(Statement statement, String why) {
        SQLException refused = catchThrowableOfType(SQLException.class, statement::run);
        assertThat((Throwable) refused).as("refused: " + why).isNotNull();
        assertThat(refused.getSQLState()).as(why + ": " + refused.getMessage()).isEqualTo(RESTRICT_VIOLATION);
        assertThat(refused.getMessage()).as(why + ": the trigger's message").contains("append-only");
    }

    @Test
    @DisplayName("#1371 完成标准 15（设计 4.1、7）：迁入归属行把 employer_confirmed_at 由空改成一个值，其余各列不变，被接受")
    void aMigratedRowAcceptsItsOneConfirmation() throws Exception {
        UUID id = row(legalEntity, "MIGRATION_ATTRIBUTION");
        Map<String, Object> before = rowOf(id);
        assertThat(before.get("employer_confirmed_at")).as("a backfilled row is unconfirmed").isNull();

        set("employer_confirmed_at = ?", id, at.plusDays(1));

        Map<String, Object> after = rowOf(id);
        assertThat(after.get("employer_confirmed_at")).as("design 7: the confirmation is recorded").isNotNull();
        before.remove("employer_confirmed_at");
        after.remove("employer_confirmed_at");
        assertThat(after).as("design 4.1: every other column is unchanged").isEqualTo(before);
    }

    @Test
    @DisplayName("#1371 完成标准 15：已确认的迁入行再改一次被拒，无论是改确认时刻、清空还是改回空")
    void aSecondUpdateOfAConfirmedRowIsRefused() throws Exception {
        UUID id = row(legalEntity, "MIGRATION_ATTRIBUTION");
        set("employer_confirmed_at = ?", id, at.plusDays(1));
        Map<String, Object> confirmed = rowOf(id);

        refused(() -> set("employer_confirmed_at = ?", id, at.plusDays(2)), "design 15: a second confirmation moves the confirmed time");
        refused(() -> set("employer_confirmed_at = null", id), "design 15: the confirmation is not taken back");

        assertThat(rowOf(id)).as("design 15: the row is as the one confirmation left it").isEqualTo(confirmed);
    }

    @Test
    @DisplayName("#1371 完成标准 15（设计 4.2-2、7）：确认时同时改用人单位被拒；单独改用人单位或其他列也被拒，行不变")
    void anUpdateOfTheEmployerOrAnyOtherColumnIsRefused() throws Exception {
        UUID id = row(legalEntity, "MIGRATION_ATTRIBUTION");
        Map<String, Object> before = rowOf(id);

        refused(() -> set("employer_confirmed_at = ?, employer_legal_entity_id = ?", id, at.plusDays(1), otherEmployer), "design 15: the confirmation step cannot change the employer");
        refused(() -> set("employer_legal_entity_id = ?", id, otherEmployer), "design 4.2-2: the employer is never re-pointed");
        refused(() -> set("employer_confirmed_at = ?, created_at = ?", id, at.plusDays(1), at.minusDays(1)), "design 15: every other column stays unchanged in the confirmation step");
        refused(() -> set("employer_source_kind = 'DECLARED'", id), "design 15: the source kind of a migrated row is not rewritten");

        assertThat(rowOf(id)).as("design 15: no refused update left a trace").isEqualTo(before);
    }

    @Test
    @DisplayName("#1371 完成标准 15：声明行（DECLARED）的任何更新都被触发器拒绝：确认、改用人单位、改其他列")
    void aDeclaredRowAcceptsNoUpdate() throws Exception {
        UUID id = row(legalEntity, "DECLARED");
        Map<String, Object> before = rowOf(id);

        refused(() -> set("employer_confirmed_at = ?", id, at.plusDays(1)), "design 15: a declared row is never confirmed");
        refused(() -> set("employer_legal_entity_id = ?", id, otherEmployer), "design 15: nor is its employer rewritten");
        refused(() -> set("updated_at = ?", id, at.plusDays(1)), "design 15: nor any other column");

        assertThat(rowOf(id)).as("design 15: the declared row is as it was").isEqualTo(before);
    }
}
