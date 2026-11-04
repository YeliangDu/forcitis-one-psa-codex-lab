package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.audit.AuditPartitionsTestSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole chain with nothing replaced: the key, the profiles, the database forcitis_one_dev and its account one_dev_app, a child process of the real main
 * that runs grant-role without a witness. A container of its own, so that the start-up audit entries of the other start-up tests are not disturbed.
 */
@Testcontainers
class SingleOperatorEndToEndIT extends AccountsAcceptanceSupport {

    static final String KEY = "forcitis.platform.identity.single-operator-mode";
    static final String PASSWORD = UUID.randomUUID().toString();
    static final String DATABASE = "forcitis_one_dev";
    static final String USER = "one_dev_app";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval")
            .withDatabaseName(DATABASE).withUsername(USER).withPassword(PASSWORD)
            .withCommand("sh", "-c", "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -out /var/lib/postgresql/server.crt -keyout /var/lib/postgresql/server.key"
                    + " && chmod 600 /var/lib/postgresql/server.key && chown postgres:postgres /var/lib/postgresql/server.key /var/lib/postgresql/server.crt"
                    + " && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/var/lib/postgresql/server.crt -c ssl_key_file=/var/lib/postgresql/server.key");

    private static List<String> commandLine(String mode, String... command) {
        String url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + DATABASE + "?sslmode=require";
        List<String> arguments = new ArrayList<>(List.of("--spring.profiles.active=dev", "--spring.profiles.include=identity-operations", "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + USER, "--spring.datasource.password=" + PASSWORD));
        for (String property : SingleOperatorModeEnvironment.JOBS_OFF) {
            arguments.add("--" + property);
        }
        if (mode != null) {
            arguments.add("--" + KEY + "=" + mode);
        }
        arguments.addAll(List.of(command));
        return arguments;
    }

    /** The operations profile does not create audit partitions; the child process needs the running UTC month, which the baseline fixes only up to 2027-03. */
    private static void prebuildCurrentMonthAuditPartition() throws Exception {
        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            AuditPartitionsTestSupport.ensureCurrentMonth(admin);
        }
    }

    private long rolesHeld(UUID employee, String roleCode) throws Exception {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement count = identity.prepareStatement("""
                select count(*) from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id
                 join platform_identity.user_accounts ua on ua.user_id = ar.user_id where ua.employee_id = ? and r.code = ? and ar.effective_to_at is null""")) {
            count.setObject(1, stored(employee));
            count.setString(2, roleCode);
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private List<Map<String, Object>> audit(String action) throws Exception {
        return auditQuery(null, null, action);
    }

    // RULE-426
    @Test
    @DisplayName("SINGLE-OP P2-22 端到端：identity-operations+dev、键为 true、库名 forcitis_one_dev、用户 one_dev_app，子进程运行 grant-role 不带 --witness：退出码 0、进程自行结束；库内出现授予、正常审计、标注审计，启动 WARN 与 enabled 审计各一；不带 process-administrator-required=false 也能完成")
    void p222_grantRoleWithoutWitnessEndToEnd() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        UUID operator = newEmployeeId();
        employee(operator, unit, null);
        prebuildCurrentMonthAuditPartition();
        int enabledBefore = audit("platform.identity.single-operator-mode.enabled").size();

        SingleOperatorModeProcess.Ended ended = SingleOperatorModeProcess.runToEnd(commandLine("true", "--operation=grant-role", "--employee-id=" + text(person),
                "--role-code=business_admin", "--operator=" + operator));

        assertThat(ended.exitCode()).as(ended.output()).isZero();
        assertThat(ended.output()).contains("role granted");
        assertThat(ended.lines().stream().filter(line -> line.contains("ADR-0030")).toList()).as("one start-up WARN line").singleElement().asString().contains("WARN");
        assertThat(rolesHeld(person, "business_admin")).as("the grant took effect").isEqualTo(1);
        UUID account = accountsOfEmployee(person).getFirst();
        assertThat(audit("platform.identity.operations.account.role.granted")).filteredOn(entry -> String.valueOf(entry.get("detail")).contains(operator.toString()))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.get("detail")).isEqualTo("operatorId=" + operator + " witnessId=none role=business_admin");
                    assertThat(entry.get("subjectId")).isEqualTo(account.toString());
                });
        assertThat(audit("platform.identity.single-operator-mode.applied")).filteredOn(entry -> account.toString().equals(entry.get("subjectId")))
                .singleElement().satisfies(entry -> assertThat(entry.get("detail")).isEqualTo("relaxation=WITNESS_OMITTED"));
        List<Map<String, Object>> enabled = audit("platform.identity.single-operator-mode.enabled");
        assertThat(enabled).as("one start-up announcement for this start").hasSize(enabledBefore + 1);
        assertThat(enabled).allSatisfy(entry -> assertThat(entry.get("actorId")).isNull());
    }

    // RULE-422
    @Test
    @DisplayName("SINGLE-OP P2-22 同一命令键未设置时退出码 1（须有见证人）且无变化：无授予、无账号、无标注、无 enabled 审计、无 ADR-0030 的 WARN")
    void p222_theSameCommandWithoutTheKeyIsRefused() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        int enabledBefore = audit("platform.identity.single-operator-mode.enabled").size();
        int marksBefore = audit("platform.identity.single-operator-mode.applied").size();
        UUID operator = newEmployeeId();
        employee(operator, unit, null);

        SingleOperatorModeProcess.Ended ended = SingleOperatorModeProcess.runToEnd(commandLine(null, "--operation=grant-role", "--employee-id=" + text(person),
                "--role-code=business_admin", "--operator=" + operator));

        assertThat(ended.exitCode()).as(ended.output()).isEqualTo(1);
        assertThat(ended.output()).contains("PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED").doesNotContain("ADR-0030");
        assertThat(accountsOfEmployee(person)).as("no account was opened").isEmpty();
        assertThat(audit("platform.identity.single-operator-mode.enabled")).hasSize(enabledBefore);
        assertThat(audit("platform.identity.single-operator-mode.applied")).hasSize(marksBefore);
    }
}
