package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The real audit trail: the normal entry and the mark of an omitted witness are written together or not at all. */
@Testcontainers
@Import(SingleOperatorIdentitySupport.Switches.class)
class SingleOperatorMarkTransactionIT extends AccountsAcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

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
        UUID operator = UUID.randomUUID();

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
        UUID operator = UUID.randomUUID();
        long marksBefore = marks();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            grantWithoutWitness(person, operator);
            throw new IllegalStateException("the caller fails after the command wrote its audit entries");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("after the command wrote");

        assertThat(accountsOfEmployee(person)).as("no account, so no grant").isEmpty();
        assertThat(entries("platform.identity.operations.account.role.granted")).noneMatch(entry -> String.valueOf(entry.get("detail")).contains(operator.toString()));
        assertThat(marks()).as("no mark either").isEqualTo(marksBefore);
    }
}
