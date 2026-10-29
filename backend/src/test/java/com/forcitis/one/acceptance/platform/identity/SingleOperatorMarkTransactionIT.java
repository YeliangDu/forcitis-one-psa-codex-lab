package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** The real audit trail: the normal entry and the mark of an omitted witness are written together or not at all. */
@Import(SingleOperatorIdentitySupport.Switches.class)
class SingleOperatorMarkTransactionIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(SingleOperatorMarkTransactionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @org.springframework.test.context.DynamicPropertySource
    static void totpKey(org.springframework.test.context.DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        registry.add("forcitis.platform.identity.emergency.totp-encryption-key", () -> java.util.Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    SingleOperatorIdentitySupport.ModeSwitch modeSwitch;

    @Autowired
    OperationsCommands commands;

    @Autowired
    TransactionTemplate transaction;

    UUID unit;

    @BeforeEach
    void modeOnAndADepartment() throws Exception {
        modeSwitch.turn(true);
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
    }

    private UUID newPerson() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        return person;
    }

    private int grantWithoutWitness(UUID person, UUID operator) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = commands.run(new DefaultApplicationArguments("--operation=grant-role", "--employee-id=" + text(person), "--role-code=business_admin", "--operator=" + operator),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        assertThat(exit).as(err.toString(StandardCharsets.UTF_8)).isZero();
        return exit;
    }

    private List<Map<String, Object>> entries(String action) throws Exception {
        return auditQuery(null, null, action);
    }

    private long marks() throws Exception {
        return entries("platform.identity.single-operator-mode.applied").size();
    }

    @Test
    @DisplayName("SINGLE-OP P2-02 模式开启省略见证人并提交：真实审计里正常条目与标注条目各一条，主体（种类与 ID）相同，标注 detail 恰为 relaxation=WITNESS_OMITTED，正常条目 detail 为 operatorId=<UUID> witnessId=none role=<码>")
    void p202_committedTogether() throws Exception {
        UUID person = newPerson();
        UUID operator = newPerson();

        grantWithoutWitness(person, operator);

        UUID account = accountsOfEmployee(person).getFirst();
        List<Map<String, Object>> normal = entries("platform.identity.operations.account.role.granted").stream()
                .filter(entry -> String.valueOf(entry.get("detail")).contains("operatorId=" + operator)).toList();
        assertThat(normal).singleElement().satisfies(entry -> {
            assertThat(entry.get("detail")).isEqualTo("operatorId=" + operator + " witnessId=none role=business_admin");
            assertThat(entry.get("subjectId")).isEqualTo(account.toString());
        });
        List<Map<String, Object>> marked = entries("platform.identity.single-operator-mode.applied").stream()
                .filter(entry -> account.toString().equals(entry.get("subjectId"))).toList();
        assertThat(marked).singleElement().satisfies(entry -> {
            assertThat(entry.get("detail")).isEqualTo("relaxation=WITNESS_OMITTED");
            assertThat(entry.get("subjectKind")).isEqualTo(normal.getFirst().get("subjectKind"));
        });
    }

    @Test
    @DisplayName("SINGLE-OP P2-02 命令在写完正常审计后失败回滚时两条都不留：调用方事务回滚，账号、授予、正常审计、标注审计全部不存在（证明二者加入调用方事务，而非独立提交）")
    void p202_rolledBackTogether() throws Exception {
        UUID person = newPerson();
        UUID operator = newPerson();
        long marksBefore = marks();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            grantWithoutWitness(person, operator);
            throw new IllegalStateException("the caller fails after the command wrote its audit entries");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("after the command wrote");

        assertThat(accountsOfEmployee(person)).as("no account, so no grant").isEmpty();
        assertThat(entries("platform.identity.operations.account.role.granted")).noneMatch(entry -> String.valueOf(entry.get("detail")).contains(operator.toString()));
        assertThat(marks()).as("no mark either").isEqualTo(marksBefore);
    }

    private com.forcitis.one.kernel.EmployeeId operatorWithoutWitnessId(UUID operator) {
        return com.forcitis.one.kernel.EmployeeId.parse(operator.toString());
    }

    private static void inWorkContext(Runnable action) {
        try (com.forcitis.one.kernel.WorkContext.Scope scope = com.forcitis.one.kernel.WorkContext.enter(UUID.randomUUID(), null, "platform.identity.operations")) {
            action.run();
        }
    }

    private int attestedCommand(String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = commands.run(new DefaultApplicationArguments(arguments), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(out, true, StandardCharsets.UTF_8));
        assertThat(exit).as(out.toString(StandardCharsets.UTF_8)).isZero();
        return exit;
    }

    private long rolesHeld(UUID employee, String roleCode) throws Exception {
        try (java.sql.Connection identity = schemaConnection("platform_identity"); java.sql.PreparedStatement count = identity.prepareStatement("""
                select count(*) from platform_identity.account_roles ar join platform_identity.roles r on r.role_id = ar.role_id
                 join platform_identity.user_accounts ua on ua.user_id = ar.user_id where ua.employee_id = ? and r.code = ?""")) {
            count.setObject(1, stored(employee));
            count.setString(2, roleCode);
            try (java.sql.ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Autowired
    com.forcitis.one.platform.identity.application.GrantRoleByOperations grant;

    @Autowired
    com.forcitis.one.platform.identity.application.RevokeRoleByOperations revoke;

    @Autowired
    com.forcitis.one.platform.identity.application.DisableEmergencyAdmin disable;

    @Test
    @DisplayName("SINGLE-OP P2-02 模式关闭时无见证人的 OperationAttestation 直接调用 GrantRoleByOperations.execute：被拒（IllegalStateException），无账号、无授予、无正常审计、无标注，事务整体回滚")
    void p202_grantWithoutWitnessIsRefusedWithTheModeOff() throws Exception {
        modeSwitch.turn(false);
        UUID person = newPerson();
        UUID operator = UUID.randomUUID();
        long marksBefore = marks();

        assertThatThrownBy(() -> inWorkContext(() -> grant.execute(new com.forcitis.one.platform.identity.application.OperationAttestation(operatorWithoutWitnessId(operator), null),
                employeeIdOf(person), "business_admin"))).isInstanceOf(IllegalStateException.class);

        assertThat(accountsOfEmployee(person)).isEmpty();
        assertThat(entries("platform.identity.operations.account.role.granted")).noneMatch(entry -> String.valueOf(entry.get("detail")).contains(operator.toString()));
        assertThat(marks()).isEqualTo(marksBefore);
    }

    @Test
    @DisplayName("SINGLE-OP P2-02 模式关闭时无见证人直接调用 RevokeRoleByOperations.execute：被拒，角色仍被持有，无收回审计、无标注")
    void p202_revokeWithoutWitnessIsRefusedWithTheModeOff() throws Exception {
        modeSwitch.turn(false);
        UUID person = newPerson();
        attestedCommand("--operation=grant-role", "--employee-id=" + text(person), "--role-code=business_admin", "--operator=" + newPerson(), "--witness=" + newPerson());
        UUID operator = UUID.randomUUID();
        long marksBefore = marks();

        assertThatThrownBy(() -> inWorkContext(() -> revoke.execute(new com.forcitis.one.platform.identity.application.OperationAttestation(operatorWithoutWitnessId(operator), null),
                employeeIdOf(person), "business_admin"))).isInstanceOf(IllegalStateException.class);

        assertThat(rolesHeld(person, "business_admin")).isEqualTo(1);
        assertThat(entries("platform.identity.operations.account.role.revoked")).noneMatch(entry -> String.valueOf(entry.get("detail")).contains(operator.toString()));
        assertThat(marks()).isEqualTo(marksBefore);
    }

    @Test
    @DisplayName("SINGLE-OP P2-02 模式关闭时无见证人直接调用 DisableEmergencyAdmin.execute：被拒，应急账号仍启用（随后带见证人的 disable 仍得到「disabled」而非「already disabled」），无停用审计、无标注")
    void p202_disableWithoutWitnessIsRefusedWithTheModeOff() throws Exception {
        modeSwitch.turn(false);
        UUID person = newPerson();
        attestedCommand("--operation=grant-role", "--employee-id=" + text(person), "--role-code=super_admin", "--operator=" + newPerson(), "--witness=" + newPerson());
        attestedCommand("--operation=register-emergency-admin", "--employee-id=" + text(person), "--login-name=breakglass", "--operator=" + newPerson(), "--witness=" + newPerson());
        UUID operator = UUID.randomUUID();
        long marksBefore = marks();

        assertThatThrownBy(() -> inWorkContext(() -> disable.execute(new com.forcitis.one.platform.identity.application.OperationAttestation(operatorWithoutWitnessId(operator), null),
                employeeIdOf(person)))).isInstanceOf(IllegalStateException.class);

        assertThat(entries("platform.identity.operations.emergency-admin.disabled")).noneMatch(entry -> String.valueOf(entry.get("detail")).contains(operator.toString()));
        assertThat(marks()).isEqualTo(marksBefore);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = commands.run(new DefaultApplicationArguments("--operation=disable-emergency-admin", "--employee-id=" + text(person), "--operator=" + newPerson(), "--witness=" + newPerson()),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(out, true, StandardCharsets.UTF_8));
        assertThat(exit).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("disabled\n");
    }
}
