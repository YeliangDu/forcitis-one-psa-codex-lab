package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P3b, ADR-0033 guard 9 and section 3 item 6: the operations commands name the person by the employee id, which is a UUID in its
 * canonical form. Any other form (the old whole number, a negative number, upper case) is wrong usage, exit code 2, and writes nothing; an
 * employee that does not exist is refused and writes nothing; a valid id opens or reuses the account. The read-only lookup by an external
 * subject (RULING-P3B-D8) is a command of platform.integration and is tested there (ExternalSubjectLookupCommandIT, RULING-P3B-7).
 */
@Testcontainers
class OperationsCommandEmployeeIdIT extends AccountsAcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    static final int WRONG_USAGE = 2;

    @Autowired
    OperationsCommands operations;

    UUID unit;

    record Outcome(int exit, String out, String err) {
    }

    @BeforeEach
    void aDepartment() throws SQLException {
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
    }

    private Outcome run(String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<String> all = new ArrayList<>(List.of(arguments));
        all.add("--operator=01900000-0000-7000-8000-00000000a001");
        all.add("--witness=01900000-0000-7000-8000-00000000a002");
        int exit = operations.run(new DefaultApplicationArguments(all.toArray(String[]::new)), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Outcome(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Outcome grant(String employeeId) {
        return run("--operation=grant-role", "--employee-id=" + employeeId, "--role-code=business_admin");
    }

    /** Rows of the identity tables a command could write. */
    private List<Integer> identityRows() throws SQLException {
        List<Integer> rows = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity")) {
            for (String table : List.of("user_accounts", "account_roles", "emergency_admins", "authorization_requests")) {
                try (PreparedStatement count = identity.prepareStatement("select count(*) from " + table); ResultSet rs = count.executeQuery()) {
                    rs.next();
                    rows.add(rs.getInt(1));
                }
            }
        }
        return rows;
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 9：--employee-id 不是规范 uuid（旧整数形态、负数、大写 uuid）即用法错误，退出码 2，不写任何数据")
    void anEmployeeIdThatIsNoCanonicalUuidIsWrongUsage() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        List<Integer> before = identityRows();

        for (String wrong : List.of(String.valueOf(serialOf(person)), "-" + serialOf(person), person.toString().toUpperCase(Locale.ROOT))) {
            Outcome outcome = grant(wrong);

            assertThat(outcome.exit()).as("--employee-id=%s: %s", wrong, outcome.err()).isEqualTo(WRONG_USAGE);
            assertThat(identityRows()).as("nothing written for --employee-id=%s", wrong).isEqualTo(before);
        }
        assertThat(accountsOfEmployee(person)).isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 9：--employee-id 是规范 uuid 但员工不存在，退出码非 0，不写任何数据")
    void anEmployeeThatDoesNotExistIsRefused() throws Exception {
        List<Integer> before = identityRows();

        Outcome outcome = grant(UUID.randomUUID().toString());

        assertThat(outcome.exit()).as(outcome.err()).isNotZero();
        assertThat(identityRows()).isEqualTo(before);
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 9：--employee-id 为在职员工的规范 uuid 时取得或建立账号并授角色；再执行一次不另建账号")
    void aValidEmployeeIdOpensOrReusesTheAccount() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);

        Outcome first = grant(person.toString());

        assertThat(first.exit()).as(first.err()).isZero();
        List<UUID> accounts = accountsOfEmployee(person);
        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst()).as("the account id is not the employee id").isNotEqualTo(person);

        Outcome again = run("--operation=grant-role", "--employee-id=" + person, "--role-code=org_data_admin");

        assertThat(again.exit()).as(again.err()).isZero();
        assertThat(accountsOfEmployee(person)).containsExactlyElementsOf(accounts);
    }
}
