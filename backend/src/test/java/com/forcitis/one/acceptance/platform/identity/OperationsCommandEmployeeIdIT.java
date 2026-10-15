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
 * employee that does not exist is refused and writes nothing; a valid id opens or reuses the account. RULING-P3B-D8 adds the read-only
 * command that lists the employee ids and user ids of the people an organisation login name or a WeCom user id belongs to.
 */
@Testcontainers
class OperationsCommandEmployeeIdIT extends AccountsAcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    /** Command and argument names of RULING-P3B-D8 (one-glossary 2026-10-15); the argument name is this test's proposal. */
    static final String FIND_ACCOUNTS = "find-accounts-by-external-subject";
    static final String EXTERNAL_SUBJECT = "external-subject";
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
        all.add("--operator=acceptance-operator");
        all.add("--witness=acceptance-witness");
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

    private void loginName(UUID employee, String loginName) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce");
             PreparedStatement update = workforce.prepareStatement("update employees set login_name = ? where id = ?")) {
            update.setString(1, loginName);
            update.setObject(2, stored(employee));
            update.executeUpdate();
        }
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

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 6 条 / RULING-P3B-D8：只读命令按组织源登录名返回匹配的员工 ID 与 user_id 列表（同名两人都列出），不写任何数据")
    void theReadOnlyCommandListsEveryoneWithTheLoginName() throws Exception {
        UUID first = newEmployeeId();
        UUID second = newEmployeeId();
        UUID other = newEmployeeId();
        employee(first, unit, null);
        employee(second, unit, null);
        employee(other, unit, null);
        String shared = "acceptance-login-" + serialOf(first);
        loginName(first, shared);
        loginName(second, shared);
        loginName(other, "acceptance-login-" + serialOf(other));
        UUID firstAccount = signIn(first).userId();
        List<Integer> before = identityRows();

        Outcome found = run("--operation=" + FIND_ACCOUNTS, "--" + EXTERNAL_SUBJECT + "=" + shared);

        assertThat(found.exit()).as(found.err()).isZero();
        assertThat(found.out()).contains(first.toString(), firstAccount.toString(), second.toString()).doesNotContain(other.toString());
        assertThat(identityRows()).as("a read-only command writes nothing").isEqualTo(before);
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 6 条 / RULING-P3B-D8：只读命令按企业微信 userid 返回员工 ID 与 user_id；查无此人时不列出任何员工")
    void theReadOnlyCommandFindsThePersonByWecomUserId() throws Exception {
        UUID person = newEmployeeId();
        UUID other = newEmployeeId();
        employee(person, unit, null);
        employee(other, unit, null);
        UUID account = signIn(person).userId();

        Outcome found = run("--operation=" + FIND_ACCOUNTS, "--" + EXTERNAL_SUBJECT + "=" + wecomUserIdOf(person));
        Outcome nobody = run("--operation=" + FIND_ACCOUNTS, "--" + EXTERNAL_SUBJECT + "=acceptance-nobody-" + serialOf(person));

        assertThat(found.exit()).as(found.err()).isZero();
        assertThat(found.out()).contains(person.toString(), account.toString()).doesNotContain(other.toString());
        assertThat(nobody.out()).doesNotContain(person.toString()).doesNotContain(other.toString());
    }
}
