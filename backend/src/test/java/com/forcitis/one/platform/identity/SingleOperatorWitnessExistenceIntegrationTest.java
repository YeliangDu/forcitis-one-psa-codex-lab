package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.SingleOperatorMode;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(SingleOperatorWitnessExistenceIntegrationTest.ModeOn.class)
@DisplayName("OperationsCommands 单人模式下的见证人存在性：给出的见证人必须存在，完全省略才免检")
class SingleOperatorWitnessExistenceIntegrationTest extends IdentityIntegrationTest {
    static final String OPERATOR_ID = "01900000-0000-7000-8000-00000000a001";
    static final String UNKNOWN_WITNESS_ID = "01900000-0000-7000-8000-00000000a0ff";
    static final String APPLIED = "platform.identity.single-operator-mode.applied";

    @TestConfiguration(proxyBeanMethods = false)
    static class ModeOn {
        @Bean
        @Primary
        SingleOperatorMode singleOperatorModeOn() {
            return () -> true;
        }
    }

    @Autowired
    OperationsCommands commands;

    private record Result(int exitCode, String err) {
    }

    private Result run(String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = commands.run(new DefaultApplicationArguments(arguments), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, err.toString(StandardCharsets.UTF_8));
    }

    private List<AuditEntry> applied() {
        return recorded().stream().filter(entry -> entry.action().equals(APPLIED)).toList();
    }

    @Test
    @DisplayName("模式开启且给了不存在的见证人：拒绝（退出码 1，WITNESS_REQUIRED），账号与审计无变化，无标注")
    void aGivenWitnessThatDoesNotExistIsRefused() {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        long accounts = count("user_accounts");
        int audited = recorded().size();

        Result result = run("--operation=grant-role", "--employee-id=" + TestAccounts.employee(1001).value(), "--role-code=business_admin",
                "--operator=" + OPERATOR_ID, "--witness=" + UNKNOWN_WITNESS_ID);

        assertThat(result.exitCode()).as(result.err()).isEqualTo(1);
        assertThat(result.err()).contains("PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED");
        assertThat(count("user_accounts")).isEqualTo(accounts);
        assertThat(recorded()).hasSize(audited);
        assertThat(applied()).isEmpty();
    }

    @Test
    @DisplayName("模式开启且完全省略见证人、操作人存在：成功，写标注 relaxation=WITNESS_OMITTED")
    void anOmittedWitnessWithAnExistingOperatorSucceedsAndIsMarked() {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);

        Result result = run("--operation=grant-role", "--employee-id=" + TestAccounts.employee(1001).value(), "--role-code=business_admin",
                "--operator=" + OPERATOR_ID);

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(applied()).extracting(AuditEntry::detail).containsExactly("relaxation=WITNESS_OMITTED");
    }
}
