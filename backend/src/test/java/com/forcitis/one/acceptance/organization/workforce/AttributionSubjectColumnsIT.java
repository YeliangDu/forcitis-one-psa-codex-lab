package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P3b DA-5 (ADR-0033 section 2 item 10, section 7 item 4; card section 3.1): a legal entity attribution and its corrections name
 * their subject in one of two typed columns, org_unit_id or employee_id. Exactly one is filled (CHECK), each has its foreign key, and a
 * department or an employee has one attribution at most. Checked on the migrated schema with plain SQL; the constraint names are the
 * card's, given here once.
 */
class AttributionSubjectColumnsIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(AttributionSubjectColumnsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String CHECK_VIOLATION = "23514";
    static final String FOREIGN_KEY_VIOLATION = "23503";
    static final String UNIQUE_VIOLATION = "23505";

    static final String ATTRIBUTION_SUBJECT = "ck_legal_entity_attributions_subject";
    static final String ATTRIBUTION_ORG_UNIT_FK = "fk_legal_entity_attributions_org_unit_id";
    static final String ATTRIBUTION_EMPLOYEE_FK = "fk_legal_entity_attributions_employee_id";
    static final String ATTRIBUTION_ORG_UNIT_UNIQUE = "uk_legal_entity_attributions_org_unit_id";
    static final String ATTRIBUTION_EMPLOYEE_UNIQUE = "uk_legal_entity_attributions_employee_id";
    static final String CORRECTION_SUBJECT = "ck_legal_entity_attribution_corrections_subject";

    UUID unit;
    UUID person;

    @BeforeEach
    void aDepartmentAndAnEmployee() throws SQLException {
        unit = newOrgUnitId();
        person = newEmployeeId();
        orgUnit(unit, null, null);
        employee(person, unit, null);
    }

    private void attribution(UUID orgUnit, UUID employee) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement insert = workforce.prepareStatement(
                "insert into legal_entity_attributions (attribution_id, org_unit_id, employee_id) values (?, ?, ?)")) {
            insert.setObject(1, Uuids.timeOrdered(NOW));
            insert.setObject(2, orgUnit);
            insert.setObject(3, employee);
            insert.executeUpdate();
        }
    }

    private void correction(UUID orgUnit, UUID employee) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement insert = workforce.prepareStatement("""
                insert into legal_entity_attribution_corrections (correction_id, org_unit_id, employee_id, period_effective_from_date,
                    previous_effective_to_date, new_effective_to_date, correction_reason, corrected_by_user_id)
                values (?, ?, ?, ?, ?, ?, 'synthetic: wrong end date', ?)""")) {
            insert.setObject(1, Uuids.timeOrdered(NOW));
            insert.setObject(2, orgUnit);
            insert.setObject(3, employee);
            insert.setObject(4, TODAY.minusDays(60));
            insert.setObject(5, TODAY.minusDays(20));
            insert.setObject(6, TODAY.minusDays(10));
            insert.setObject(7, Uuids.timeOrdered(NOW));
            insert.executeUpdate();
        }
    }

    private static void refusedBy(ThrowingInsert insert, String sqlState, String constraint) {
        SQLException refused = catchThrowableOfType(SQLException.class, insert::run);
        assertThat((Throwable) refused).as("the insert is refused by %s", constraint).isNotNull();
        assertThat(refused.getSQLState()).as(refused.getMessage()).isEqualTo(sqlState);
        assertThat(refused.getMessage()).contains(constraint);
    }

    @FunctionalInterface
    interface ThrowingInsert {
        void run() throws SQLException;
    }

    @Test
    @DisplayName("R-1b P3b DA-5：法人归属的 org_unit_id / employee_id 恰有一个非空——两空、两非空都被 CHECK 拒绝；只填一个可以写入")
    void anAttributionNamesExactlyOneSubject() throws SQLException {
        refusedBy(() -> attribution(null, null), CHECK_VIOLATION, ATTRIBUTION_SUBJECT);
        refusedBy(() -> attribution(unit, person), CHECK_VIOLATION, ATTRIBUTION_SUBJECT);

        attribution(unit, null);
        attribution(null, person);
    }

    @Test
    @DisplayName("R-1b P3b DA-5：法人归属的两列各有外键——指向不存在的部门或员工被拒")
    void anAttributionPointsAtAnExistingSubject() {
        refusedBy(() -> attribution(Uuids.timeOrdered(NOW), null), FOREIGN_KEY_VIOLATION, ATTRIBUTION_ORG_UNIT_FK);
        refusedBy(() -> attribution(null, Uuids.timeOrdered(NOW)), FOREIGN_KEY_VIOLATION, ATTRIBUTION_EMPLOYEE_FK);
    }

    @Test
    @DisplayName("R-1b P3b DA-5：同一部门、同一员工各只有一条法人归属")
    void aSubjectHasOneAttributionAtMost() throws SQLException {
        attribution(unit, null);
        attribution(null, person);

        refusedBy(() -> attribution(unit, null), UNIQUE_VIOLATION, ATTRIBUTION_ORG_UNIT_UNIQUE);
        refusedBy(() -> attribution(null, person), UNIQUE_VIOLATION, ATTRIBUTION_EMPLOYEE_UNIQUE);
    }

    @Test
    @DisplayName("R-1b P3b DA-5：法人归属更正记录同样恰有一个非空（两空、两非空被 CHECK 拒绝），并有外键")
    void aCorrectionNamesExactlyOneExistingSubject() throws SQLException {
        refusedBy(() -> correction(null, null), CHECK_VIOLATION, CORRECTION_SUBJECT);
        refusedBy(() -> correction(unit, person), CHECK_VIOLATION, CORRECTION_SUBJECT);
        refusedBy(() -> correction(Uuids.timeOrdered(NOW), null), FOREIGN_KEY_VIOLATION, "legal_entity_attribution_corrections");
        refusedBy(() -> correction(null, Uuids.timeOrdered(NOW)), FOREIGN_KEY_VIOLATION, "legal_entity_attribution_corrections");

        attribution(unit, null);
        correction(unit, null);
        attribution(null, person);
        correction(null, person);
    }
}
