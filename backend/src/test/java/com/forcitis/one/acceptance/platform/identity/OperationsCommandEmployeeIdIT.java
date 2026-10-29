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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P3b, ADR-0033 guard 9 and section 3 item 6: the operations commands name the person by the employee id, which is a UUID in its
 * canonical form. Any other form (the old whole number, a negative number, upper case) is wrong usage, exit code 2, and writes nothing; an
 * employee that does not exist is refused and writes nothing; a valid id opens or reuses the account. The read-only lookup by an external
 * subject (RULING-P3B-D8) is a command of platform.integration and is tested there (ExternalSubjectLookupCommandIT, RULING-P3B-7).
 */
class OperationsCommandEmployeeIdIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(OperationsCommandEmployeeIdIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

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
        return runAs(OPERATOR_ID, WITNESS_ID, arguments);
    }

    /** A null operator or witness leaves the option out. */
    private Outcome runAs(String operator, String witness, String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<String> all = new ArrayList<>(List.of(arguments));
        if (operator != null) {
            all.add("--operator=" + operator);
        }
        if (witness != null) {
            all.add("--witness=" + witness);
        }
        ensureAttestors();
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

    static final int REFUSED = 1;
    static final String WITNESS_REQUIRED = "PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED";

    /** All audit entries whose detail starts with the text (null for every entry, also those without a detail), counted in the table, not through the capped query. */
    private long auditEntryCount(String detailPrefix) throws SQLException {
        try (Connection audit = schemaConnection("platform_audit"); PreparedStatement count = audit.prepareStatement(
                "select count(*) from audit_entries where ?::text is null or detail like ? || '%'")) {
            count.setString(1, detailPrefix);
            count.setString(2, detailPrefix);
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Runs a grant-role for a fresh employee with the given attestation; asserts it is refused, writes nothing and is not audited. */
    private void assertRefusedAndNothingDone(String operator, String witness, String why) throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        List<Integer> before = identityRows();
        long auditedBefore = auditEntryCount(null);

        Outcome outcome = runAs(operator, witness, "--operation=grant-role", "--employee-id=" + person, "--role-code=business_admin");

        assertThat(outcome.exit()).as("%s: %s", why, outcome.err()).isEqualTo(REFUSED);
        assertThat(outcome.err()).as(why).contains(WITNESS_REQUIRED);
        assertThat(identityRows()).as("%s: nothing written", why).isEqualTo(before);
        assertThat(accountsOfEmployee(person)).as("%s: no account opened", why).isEmpty();
        assertThat(auditEntryCount(null)).as("%s: no audit entry of any kind", why).isEqualTo(auditedBefore);
    }

    @Test
    @DisplayName("DR-TB01-FU1：--operator 是规范 uuid 但员工不存在 → 退出码 1，错误码 PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED，不写数据、不写审计")
    void fu1AnOperatorWhoDoesNotExistIsRefused() throws Exception {
        assertRefusedAndNothingDone(UUID.randomUUID().toString(), WITNESS_ID, "operator does not exist");
    }

    @Test
    @DisplayName("DR-TB01-FU1：--witness 是规范 uuid 但员工不存在 → 退出码 1，错误码 PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED，不写数据、不写审计")
    void fu1AWitnessWhoDoesNotExistIsRefused() throws Exception {
        assertRefusedAndNothingDone(OPERATOR_ID, UUID.randomUUID().toString(), "witness does not exist");
    }

    @Test
    @DisplayName("DR-TB01-FU1：操作人与见证人都不存在 → 同样被拒")
    void fu1NeitherExistsIsRefused() throws Exception {
        assertRefusedAndNothingDone(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "neither exists");
    }

    @Test
    @DisplayName("DR-TB01-FU1（对照，现行行为）：缺见证人、相同、非员工 ID → 退出码 1，错误码同上，不写数据、不写审计")
    void fu1MissingSameOrNotAnIdIsStillRefused() throws Exception {
        assertRefusedAndNothingDone(OPERATOR_ID, null, "witness missing");
        assertRefusedAndNothingDone(null, WITNESS_ID, "operator missing");
        assertRefusedAndNothingDone(OPERATOR_ID, OPERATOR_ID, "operator and witness the same");
        assertRefusedAndNothingDone(OPERATOR_ID, "not-an-employee-id", "witness is no employee id");
    }

    @Test
    @DisplayName("DR-TB01-FU1：两人都存在且不同 → 照常成功，审计记两人的员工 ID")
    void fu1TwoDifferentExistingEmployeesProceed() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        String detail = "operatorId=" + OPERATOR_ID + " witnessId=" + WITNESS_ID + " role=business_admin";
        long auditedBefore = auditEntryCount(detail);

        Outcome outcome = run("--operation=grant-role", "--employee-id=" + person, "--role-code=business_admin");

        assertThat(outcome.exit()).as(outcome.err()).isZero();
        assertThat(accountsOfEmployee(person)).hasSize(1);
        assertThat(auditEntryCount(detail)).as("an audit entry whose detail names the operator and the witness by employee id").isEqualTo(auditedBefore + 1);
    }

    @Test
    @DisplayName("DR-TB01-FU1：已离职但仍在快照中的员工可作操作人或见证人（只要求存在，不要求在职）")
    void fu1ASeparatedEmployeeStillCounts() throws Exception {
        UUID separated = newEmployeeId();
        employee(separated, unit, null);
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement update = workforce.prepareStatement(
                "update employees set employment_status = 'SEPARATED', separated_at = now() where id = ?")) {
            update.setObject(1, stored(separated));
            update.executeUpdate();
        }
        UUID person = newEmployeeId();
        employee(person, unit, null);

        Outcome outcome = runAs(separated.toString(), WITNESS_ID, "--operation=grant-role", "--employee-id=" + person, "--role-code=business_admin");

        assertThat(outcome.exit()).as(outcome.err()).isZero();
    }
}
