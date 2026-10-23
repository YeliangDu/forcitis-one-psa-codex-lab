package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DATA-REPAIR-workforce PR-8 (WF-04, WF-05, WF-09, WF-11, WF-12): row constraints of the workforce schema, checked as a black box through
 * the database: a statement that breaks one constraint is refused with the constraint's name and SQLSTATE, the same statement made valid
 * goes through. Every refused statement breaks exactly one constraint. Constraint names are the design section's.
 */
@Testcontainers
class WorkforceRowConstraintsIT extends AccountsAcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    static final String CHECK_VIOLATION = "23514";
    static final String FOREIGN_KEY_VIOLATION = "23503";

    UUID unit;
    UUID person;

    @BeforeEach
    void aDepartmentAndAnEmployee() throws SQLException {
        unit = newOrgUnitId();
        person = newEmployeeId();
        orgUnit(unit, null, null);
        employee(person, unit, null);
    }

    private static String letters(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
        }
        return code.toString();
    }

    private void execute(String sql, Object... args) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement statement = workforce.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    private static void refusedBy(ThrowingStatement statement, String sqlState, String constraint) {
        SQLException refused = catchThrowableOfType(SQLException.class, statement::run);
        assertThat((Throwable) refused).as("the statement is refused by %s", constraint).isNotNull();
        assertThat(refused.getSQLState()).as(refused.getMessage()).isEqualTo(sqlState);
        assertThat(refused.getMessage()).contains("\"" + constraint + "\"");
    }

    @FunctionalInterface
    interface ThrowingStatement {
        void run() throws SQLException;
    }

    private UUID legalEntity(String name) throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        execute("insert into legal_entities (id, code, name, status) values (?, ?, ?, 'ACTIVE')", id, letters(6), name);
        return id;
    }

    private void legalEntityVersion(UUID legalEntityId, String code, String name) throws SQLException {
        execute("""
                insert into legal_entity_versions (legal_entity_version_id, legal_entity_id, effective_from_date, code, name, status)
                values (?, ?, ?, ?, ?, 'ACTIVE')""", Uuids.timeOrdered(NOW), legalEntityId, TODAY.minusDays(30), code, name);
    }

    private void attribution(UUID orgUnit, UUID employee) throws SQLException {
        execute("insert into legal_entity_attributions (attribution_id, org_unit_id, employee_id) values (?, ?, ?)",
                Uuids.timeOrdered(NOW), orgUnit, employee);
    }

    private void correction(UUID orgUnit, UUID employee, LocalDate previousEnd, LocalDate newEnd) throws SQLException {
        execute("""
                insert into legal_entity_attribution_corrections (correction_id, org_unit_id, employee_id, period_effective_from_date,
                    previous_effective_to_date, new_effective_to_date, correction_reason, corrected_by_user_id)
                values (?, ?, ?, ?, ?, ?, 'synthetic: wrong end date', ?)""",
                Uuids.timeOrdered(NOW), orgUnit, employee, TODAY.minusDays(60), previousEnd, newEnd, Uuids.timeOrdered(NOW));
    }

    // ---- WF-04 ----

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-04：法人版本表 code 须为 2 至 10 个大写字母——fst、F、FSTFSTFSTFS、FS1、FST+换行被 ck_legal_entity_versions_code_format 拒绝，FST 通过")
    void wf04LegalEntityVersionCodeFormat() throws SQLException {
        UUID entity = legalEntity("Synthetic Legal Entity");
        for (String bad : new String[] {"fst", "F", "FSTFSTFSTFS", "FS1", "FST\n"}) {
            refusedBy(() -> legalEntityVersion(entity, bad, "Synthetic Legal Entity"), CHECK_VIOLATION, "ck_legal_entity_versions_code_format");
        }
        legalEntityVersion(entity, "FST", "Synthetic Legal Entity");
    }

    // ---- WF-05 ----

    private void orgUnitWithStatus(String status, String revokedAt) throws SQLException {
        execute("insert into org_units (id, name, status, revoked_at) values (?, 'Synthetic unit wf05', ?, ?::timestamptz)",
                Uuids.timeOrdered(NOW), status, revokedAt);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-05：组织单元 REVOKED 与撤销时间成对——REVOKED 无时间、ACTIVE 或 DISABLED 带时间被 ck_org_units_revoked_at 拒绝；REVOKED 带时间、ACTIVE 无时间通过")
    void wf05RevokedAtPairsWithRevokedStatus() throws SQLException {
        String stamp = "2026-10-01T00:00:00Z";
        refusedBy(() -> orgUnitWithStatus("REVOKED", null), CHECK_VIOLATION, "ck_org_units_revoked_at");
        refusedBy(() -> orgUnitWithStatus("ACTIVE", stamp), CHECK_VIOLATION, "ck_org_units_revoked_at");
        refusedBy(() -> orgUnitWithStatus("DISABLED", stamp), CHECK_VIOLATION, "ck_org_units_revoked_at");

        orgUnitWithStatus("REVOKED", stamp);
        orgUnitWithStatus("ACTIVE", null);
        orgUnitWithStatus("DISABLED", null);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-05：既有组织单元撤销后再复活——改为 REVOKED 不带时间被拒，带时间通过；改回 ACTIVE 不清空时间被拒，同时清空通过")
    void wf05UpdatesKeepTheSamePairing() throws SQLException {
        refusedBy(() -> execute("update org_units set status = 'REVOKED' where id = ?", unit), CHECK_VIOLATION, "ck_org_units_revoked_at");
        execute("update org_units set status = 'REVOKED', revoked_at = now() where id = ?", unit);
        refusedBy(() -> execute("update org_units set status = 'ACTIVE' where id = ?", unit), CHECK_VIOLATION, "ck_org_units_revoked_at");
        execute("update org_units set status = 'ACTIVE', revoked_at = null where id = ?", unit);
    }

    // ---- WF-09 ----

    private void orgUnitVersion(String parent, String head, String manager) throws SQLException {
        execute("""
                insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, status, is_delivery_unit,
                    parent_org_unit_id, head_employee_id, regional_receivables_manager_id)
                values (?, ?, ?, 'Synthetic unit', 'ACTIVE', false, ?::uuid, ?::uuid, ?::uuid)""",
                Uuids.timeOrdered(NOW), unit, TODAY.minusDays(30), parent, head, manager);
    }

    private void assignmentVersion(String position, String reportsTo) throws SQLException {
        execute("""
                insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                    reports_to_employee_id)
                values (?, ?, ?, ?, ?::uuid, ?::uuid)""",
                Uuids.timeOrdered(NOW), person, TODAY.minusDays(30), unit, position, reportsTo);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-09：部门版本的上级部门指向不存在的行被 fk_org_unit_versions_parent_org_unit_id 拒绝，指向存在的部门通过")
    void wf09OrgUnitVersionParent() throws SQLException {
        refusedBy(() -> orgUnitVersion(UUID.randomUUID().toString(), null, null), FOREIGN_KEY_VIOLATION, "fk_org_unit_versions_parent_org_unit_id");
        orgUnitVersion(unit.toString(), null, null);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-09：部门版本的负责人指向不存在的员工被 fk_org_unit_versions_head_employee_id 拒绝，指向存在的员工通过")
    void wf09OrgUnitVersionHead() throws SQLException {
        refusedBy(() -> orgUnitVersion(null, UUID.randomUUID().toString(), null), FOREIGN_KEY_VIOLATION, "fk_org_unit_versions_head_employee_id");
        orgUnitVersion(null, person.toString(), null);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-09：部门版本的区域应收负责人指向不存在的员工被 fk_org_unit_versions_regional_receivables_manager_id 拒绝，指向存在的员工通过")
    void wf09OrgUnitVersionRegionalReceivablesManager() throws SQLException {
        refusedBy(() -> orgUnitVersion(null, null, UUID.randomUUID().toString()), FOREIGN_KEY_VIOLATION,
                "fk_org_unit_versions_regional_receivables_manager_id");
        orgUnitVersion(null, null, person.toString());
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-09：任职版本的岗位指向不存在的岗位被 fk_assignment_versions_position_id 拒绝，指向存在的岗位通过")
    void wf09AssignmentVersionPosition() throws SQLException {
        UUID position = UUID.randomUUID();
        execute("insert into positions (id, name, status) values (?, 'Synthetic position', 'ACTIVE')", position);
        refusedBy(() -> assignmentVersion(UUID.randomUUID().toString(), null), FOREIGN_KEY_VIOLATION, "fk_assignment_versions_position_id");
        assignmentVersion(position.toString(), null);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-09：任职版本的直属领导指向不存在的员工被 fk_assignment_versions_reports_to_employee_id 拒绝，指向存在的员工通过")
    void wf09AssignmentVersionReportsTo() throws SQLException {
        refusedBy(() -> assignmentVersion(null, UUID.randomUUID().toString()), FOREIGN_KEY_VIOLATION, "fk_assignment_versions_reports_to_employee_id");
        assignmentVersion(null, person.toString());
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-09：版本表外键为延迟检查——同一事务内先写引用负责人的部门版本、后写该员工，提交通过；员工始终不写则提交时被拒")
    void wf09VersionForeignKeysAreDeferred() throws SQLException {
        UUID later = UUID.randomUUID();
        String versionSql = """
                insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, effective_to_date, name, status,
                    is_delivery_unit, head_employee_id) values (?, ?, ?, ?, 'Synthetic unit', 'ACTIVE', false, ?)""";
        try (Connection workforce = schemaConnection("organization_workforce")) {
            workforce.setAutoCommit(false);
            try (PreparedStatement version = workforce.prepareStatement(versionSql);
                 PreparedStatement employeeRow = workforce.prepareStatement("""
                    insert into employees (id, employee_code, display_name, employment_status)
                    values (?, 'DEFERRED1', 'Synthetic deferred person', 'EMPLOYED')""")) {
                version.setObject(1, Uuids.timeOrdered(NOW));
                version.setObject(2, unit);
                version.setObject(3, TODAY.minusDays(30));
                version.setObject(4, null);
                version.setObject(5, later);
                version.executeUpdate();
                employeeRow.setObject(1, later);
                employeeRow.executeUpdate();
                workforce.commit();
            }
        }
        refusedBy(() -> {
            try (Connection workforce = schemaConnection("organization_workforce")) {
                workforce.setAutoCommit(false);
                try (PreparedStatement version = workforce.prepareStatement(versionSql)) {
                    version.setObject(1, Uuids.timeOrdered(NOW));
                    version.setObject(2, unit);
                    version.setObject(3, TODAY.minusDays(90));
                    version.setObject(4, TODAY.minusDays(60));
                    version.setObject(5, UUID.randomUUID());
                    version.executeUpdate();
                    workforce.commit();
                }
            }
        }, FOREIGN_KEY_VIOLATION, "fk_org_unit_versions_head_employee_id");
    }

    // ---- WF-11 ----

    /** constraint, insert with one ? for the checked value, whether the column may be null. */
    static Stream<Arguments> blankCheckedColumns() {
        return Stream.of(
                Arguments.of("ck_positions_name", "insert into positions (id, name, status) values (uuidv7(), ?, 'ACTIVE')", false),
                Arguments.of("ck_positions_code", "insert into positions (id, code, name, status) values (uuidv7(), ?, 'Synthetic position', 'ACTIVE')", true),
                Arguments.of("ck_org_units_name", "insert into org_units (id, name, status) values (uuidv7(), ?, 'ACTIVE')", false),
                Arguments.of("ck_org_units_code", "insert into org_units (id, code, name, status) values (uuidv7(), ?, 'Synthetic unit', 'ACTIVE')", true),
                Arguments.of("ck_org_units_short_name",
                        "insert into org_units (id, short_name, name, status) values (uuidv7(), ?, 'Synthetic unit', 'ACTIVE')", true),
                Arguments.of("ck_employees_employee_code",
                        "insert into employees (id, employee_code, display_name, employment_status) values (uuidv7(), ?, 'Synthetic person', 'EMPLOYED')", false),
                Arguments.of("ck_employees_display_name",
                        "insert into employees (id, employee_code, display_name, employment_status) values (uuidv7(), 'WF11-' || uuidv7()::text, ?, 'EMPLOYED')", false),
                Arguments.of("ck_employees_email_address", """
                        insert into employees (id, employee_code, display_name, email, employment_status)
                        values (uuidv7(), 'WF11-' || uuidv7()::text, 'Synthetic person', ?, 'EMPLOYED')""", true));
    }

    @ParameterizedTest(name = "DATA-REPAIR-workforce WF-11：{0} 拒绝空串与纯空格，带内容的值通过")
    @MethodSource("blankCheckedColumns")
    void wf11BlankTextIsRefused(String constraint, String insert, boolean nullable) throws SQLException {
        refusedBy(() -> execute(insert, ""), CHECK_VIOLATION, constraint);
        refusedBy(() -> execute(insert, "   "), CHECK_VIOLATION, constraint);
        execute(insert, "Synthetic value");
        execute(insert, "  padded  ");
        if (nullable) {
            execute(insert, (Object) null);
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-11：ck_org_unit_versions_name 与 ck_org_unit_versions_code 拒绝空串与纯空格（编码可空）")
    void wf11OrgUnitVersionNameAndCode() throws SQLException {
        String name = """
                insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, status, is_delivery_unit)
                values (uuidv7(), ?, current_date, ?, 'ACTIVE', false)""";
        String code = """
                insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, code, name, status, is_delivery_unit)
                values (uuidv7(), ?, current_date, ?, 'Synthetic unit', 'ACTIVE', false)""";
        refusedBy(() -> execute(name, unit, ""), CHECK_VIOLATION, "ck_org_unit_versions_name");
        refusedBy(() -> execute(name, unit, "   "), CHECK_VIOLATION, "ck_org_unit_versions_name");
        refusedBy(() -> execute(code, unit, ""), CHECK_VIOLATION, "ck_org_unit_versions_code");
        refusedBy(() -> execute(code, unit, "   "), CHECK_VIOLATION, "ck_org_unit_versions_code");
        execute(code, unit, null);
        UUID other = newOrgUnitId();
        orgUnit(other, null, null);
        execute(name, other, "Synthetic unit");
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-11：法人名称最多 100 个字符——101 个被 ck_legal_entities_name 拒绝，100 个通过")
    void wf11LegalEntityNameLength() throws SQLException {
        String insert = "insert into legal_entities (id, code, name, status) values (uuidv7(), ?, ?, 'ACTIVE')";
        refusedBy(() -> execute(insert, letters(6), "A".repeat(101)), CHECK_VIOLATION, "ck_legal_entities_name");
        execute(insert, letters(6), "A".repeat(100));
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-11：法人名称为空串或纯空格被 ck_legal_entities_name 拒绝")
    void wf11LegalEntityNameNotBlank() {
        String insert = "insert into legal_entities (id, code, name, status) values (uuidv7(), ?, ?, 'ACTIVE')";
        refusedBy(() -> execute(insert, letters(6), ""), CHECK_VIOLATION, "ck_legal_entities_name");
        refusedBy(() -> execute(insert, letters(6), "   "), CHECK_VIOLATION, "ck_legal_entities_name");
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-11：法人版本名称最多 100 个字符、不得为空白——101 个、空串、纯空格被 ck_legal_entity_versions_name 拒绝，100 个通过")
    void wf11LegalEntityVersionName() throws SQLException {
        UUID entity = legalEntity("Synthetic Legal Entity");
        refusedBy(() -> legalEntityVersion(entity, "FST", "A".repeat(101)), CHECK_VIOLATION, "ck_legal_entity_versions_name");
        refusedBy(() -> legalEntityVersion(entity, "FST", ""), CHECK_VIOLATION, "ck_legal_entity_versions_name");
        refusedBy(() -> legalEntityVersion(entity, "FST", "   "), CHECK_VIOLATION, "ck_legal_entity_versions_name");
        legalEntityVersion(entity, "FST", "A".repeat(100));
    }

    // ---- WF-12 ----

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-12：部门法人归属期间须有根归属——部门存在但无根时被 fk_org_unit_legal_entities_root_org_unit_id 拒绝，补建根后通过")
    void wf12OrgUnitPeriodNeedsRoot() throws SQLException {
        UUID entity = legalEntity("Synthetic Legal Entity");
        String insert = "insert into org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, effective_from_date, legal_entity_id) values (?, ?, ?, ?)";
        refusedBy(() -> execute(insert, Uuids.timeOrdered(NOW), unit, TODAY.minusDays(30), entity), FOREIGN_KEY_VIOLATION,
                "fk_org_unit_legal_entities_root_org_unit_id");
        attribution(unit, null);
        execute(insert, Uuids.timeOrdered(NOW), unit, TODAY.minusDays(30), entity);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-12：员工法人归属期间须有根归属——员工存在但无根时被 fk_employee_legal_entities_root_employee_id 拒绝，补建根后通过")
    void wf12EmployeePeriodNeedsRoot() throws SQLException {
        UUID entity = legalEntity("Synthetic Legal Entity");
        String insert = "insert into employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id) values (?, ?, ?, ?)";
        refusedBy(() -> execute(insert, Uuids.timeOrdered(NOW), person, TODAY.minusDays(30), entity), FOREIGN_KEY_VIOLATION,
                "fk_employee_legal_entities_root_employee_id");
        attribution(null, person);
        execute(insert, Uuids.timeOrdered(NOW), person, TODAY.minusDays(30), entity);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-12：部门的更正记录须有根归属——无根被 fk_legal_entity_attribution_corrections_root_org_unit_id 拒绝，补建根后通过")
    void wf12OrgUnitCorrectionNeedsRoot() throws SQLException {
        refusedBy(() -> correction(unit, null, TODAY.minusDays(20), TODAY.minusDays(10)), FOREIGN_KEY_VIOLATION,
                "fk_legal_entity_attribution_corrections_root_org_unit_id");
        attribution(unit, null);
        correction(unit, null, TODAY.minusDays(20), TODAY.minusDays(10));
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-12：员工的更正记录须有根归属——无根被 fk_legal_entity_attribution_corrections_root_employee_id 拒绝，补建根后通过")
    void wf12EmployeeCorrectionNeedsRoot() throws SQLException {
        refusedBy(() -> correction(null, person, TODAY.minusDays(20), TODAY.minusDays(10)), FOREIGN_KEY_VIOLATION,
                "fk_legal_entity_attribution_corrections_root_employee_id");
        attribution(null, person);
        correction(null, person, TODAY.minusDays(20), TODAY.minusDays(10));
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-12：空更正（新旧结束日相同）被 ck_legal_entity_attribution_corrections_changes 拒绝，结束日不同通过")
    void wf12EmptyCorrectionIsRefused() throws SQLException {
        attribution(unit, null);
        refusedBy(() -> correction(unit, null, TODAY.minusDays(20), TODAY.minusDays(20)), CHECK_VIOLATION,
                "ck_legal_entity_attribution_corrections_changes");
        correction(unit, null, TODAY.minusDays(20), TODAY.minusDays(19));
    }
}
