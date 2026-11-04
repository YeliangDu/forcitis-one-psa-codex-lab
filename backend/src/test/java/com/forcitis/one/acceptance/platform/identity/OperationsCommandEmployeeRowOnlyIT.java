package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DR-TB01-FU1-1 (NITS-1 item 5, RULING-COORD-20261017-03): an employee exists when organization_workforce.employees has a row for the id;
 * an assignment is not required. The operator and the witness here have an employees row and no assignments row at all, so this stays red
 * until the existence check stops joining the assignment.
 */
class OperationsCommandEmployeeRowOnlyIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(OperationsCommandEmployeeRowOnlyIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    OperationsCommands operations;

    /** Only an employees row, deliberately no assignments row (ensureAttestors would add one). */
    private void employeeRowOnly(UUID employeeId) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement insert = workforce.prepareStatement(
                "insert into employees (id, employee_code, display_name, employment_status) values (?, ?, ?, 'EMPLOYED')")) {
            insert.setObject(1, stored(employeeId));
            insert.setString(2, "ROW" + serialOf(employeeId));
            insert.setString(3, "Synthetic row-only person " + serialOf(employeeId));
            insert.executeUpdate();
        }
    }

    private long assignmentsOf(UUID employeeId) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement count = workforce.prepareStatement(
                "select count(*) from assignments where employee_id = ?")) {
            count.setObject(1, stored(employeeId));
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // RULE-423
    @Test
    @DisplayName("NITS-1 DR-TB01-FU1-1：操作人与见证人只有员工行、没有任职 → 运维证明通过，授角色成功（存在 = employees 有行，不要求任职）")
    void nits1AnOperatorAndWitnessWithoutAssignmentsAreAccepted() throws Exception {
        UUID operator = newEmployeeId();
        UUID witness = newEmployeeId();
        UUID person = newEmployeeId();
        employeeRowOnly(operator);
        employeeRowOnly(witness);
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        employee(person, unit, null);
        assertThat(assignmentsOf(operator)).as("the operator has no assignment").isZero();
        assertThat(assignmentsOf(witness)).as("the witness has no assignment").isZero();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = operations.run(new DefaultApplicationArguments("--operation=grant-role", "--employee-id=" + person, "--role-code=business_admin",
                "--operator=" + operator, "--witness=" + witness), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(exit).as(err.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(accountsOfEmployee(person)).hasSize(1);
    }
}
