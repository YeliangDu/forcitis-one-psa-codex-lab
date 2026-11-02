package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

@DisplayName("NITS-1 项1：运维命令运行前检查当月审计分区")
class Nits1OperationsCommandsAuditPartitionIT extends IdentityIntegrationTest {
    static final String OPERATOR_ID = "01900000-0000-7000-8000-00000000a001";
    static final String WITNESS_ID = "01900000-0000-7000-8000-00000000a002";

    @Autowired
    OperationsCommands commands;

    private record Result(int exitCode, String out, String err) {
    }

    private Result run(String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = commands.run(new DefaultApplicationArguments(arguments), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private String[] grant() {
        return new String[] {"--operation=grant-role", "--employee-id=" + TestAccounts.employee(7).value(), "--role-code=business_admin",
                "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID};
    }

    private YearMonth withoutPartition() {
        clock.advance(Duration.ofDays(3650));
        return YearMonth.from(clock.instant().atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("NITS-1 当月审计分区不存在时运维命令退出码 1，文案点名月份，且不写任何身份表与审计条目")
    void aMissingPartitionRefusesBeforeAnyWrite() {
        createAccount(7);
        YearMonth month = withoutPartition();
        long accounts = count("user_accounts");
        long roles = count("account_role_grants where effective_to_at is null");

        Result result = run(grant());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).startsWith("refused:").contains(month.toString()).contains("resident application").doesNotContain("no partition of relation");
        assertThat(count("user_accounts")).isEqualTo(accounts);
        assertThat(count("account_role_grants where effective_to_at is null")).isEqualTo(roles);
        assertThat(recorded()).isEmpty();
        assertThat(recordedIndependently()).isEmpty();
        assertThat(jdbc.sql("select count(*) from pg_class where relname = ?").param("audit_entries_p" + month.toString().replace("-", "")).query(Long.class).single())
                .as("the command creates no partition").isZero();
    }

    @Test
    @DisplayName("NITS-1 当月分区存在时运维命令照常执行")
    void anExistingPartitionLetsTheCommandRun() {
        createAccount(7);

        Result result = run(grant());

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(recorded()).hasSize(1);
    }

    @Test
    @DisplayName("NITS-1 分区检查在用法错误之后：缺少 operation、未知选项与不合规的 employee-id 仍是退出码 2，不提分区")
    void usageErrorsComeBeforeThePartitionCheck() {
        withoutPartition();

        Result missingOperation = run("--operator=" + OPERATOR_ID);
        Result unknownOption = run("--operation=grant-role", "--no-such-option=1");
        Result malformedEmployeeId = run("--operation=grant-role", "--employee-id=7", "--role-code=business_admin", "--operator=" + OPERATOR_ID,
                "--witness=" + WITNESS_ID);

        assertThat(missingOperation.exitCode()).isEqualTo(2);
        assertThat(unknownOption.exitCode()).isEqualTo(2);
        assertThat(malformedEmployeeId.exitCode()).isEqualTo(2);
        assertThat(missingOperation.err() + unknownOption.err() + malformedEmployeeId.err()).doesNotContain("partition");
    }

    @Test
    @DisplayName("NITS-1 分区缺失加用法错误（未知操作、缺 --role-code、缺 --login-name）仍退出码 2，而不是 1")
    void usageErrorsAreAnsweredBeforeThePartitionCheck() {
        createAccount(7);
        withoutPartition();
        String employee = "--employee-id=" + TestAccounts.employee(7).value();
        String[] attestors = {"--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID};

        for (String[] wrong : new String[][] {{"--operation=no-such-operation", employee}, {"--operation=grant-role", employee}, {"--operation=revoke-role", employee},
                {"--operation=register-emergency-admin", employee}}) {
            String[] line = java.util.stream.Stream.concat(java.util.Arrays.stream(wrong), java.util.Arrays.stream(attestors)).toArray(String[]::new);
            Result result = run(line);
            assertThat(result.exitCode()).as(String.join(" ", wrong)).isEqualTo(2);
            assertThat(result.err()).startsWith("wrong usage:");
        }
    }
}
