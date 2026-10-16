package com.forcitis.one.acceptance.platform.identity;

import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.DEV_DATABASE;
import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.DEV_USER;
import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.Outcome;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Failing closed: a check or an announcement that cannot run ends the start, and the cause is the check or the announcement, not something else. */
class SingleOperatorModeFailClosedIT {

    static final String READ_ONLY_USER = "one_dev_readonly";

    @BeforeAll
    static void container() throws SQLException {
        SingleOperatorModeEnvironment.prepare();
        try (Connection admin = SingleOperatorModeEnvironment.connect(DEV_DATABASE, DEV_USER); Statement statement = admin.createStatement()) {
            statement.execute("DROP ROLE IF EXISTS " + READ_ONLY_USER);
            statement.execute("CREATE ROLE " + READ_ONLY_USER + " LOGIN SUPERUSER PASSWORD '" + SingleOperatorModeEnvironment.PASSWORD + "'");
            statement.execute("ALTER ROLE " + READ_ONLY_USER + " SET default_transaction_read_only = on");
        }
    }

    private static List<String> unreachable(String mode, int port) throws Exception {
        List<String> arguments = new ArrayList<>(SingleOperatorModeEnvironment.residentArguments("dev", DEV_DATABASE, DEV_USER, mode, port, false));
        int closed = SingleOperatorModeEnvironment.freePort();
        arguments.replaceAll(argument -> argument.startsWith("--spring.datasource.url=")
                ? "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + closed + "/" + DEV_DATABASE + "?sslmode=require&connectTimeout=3" : argument);
        arguments.add("--spring.datasource.hikari.connection-timeout=3000");
        return arguments;
    }

    @Test
    @DisplayName("SINGLE-OP P1-13 失败关闭：值为 true 而数据库不可达时，失败的原因是判据无法执行，输出含 the database check could not run；键为 false 时同样不可达则不含这句")
    void p113_unreachableDatabaseIsRefusedByTheCheckItself() throws Exception {
        int port = SingleOperatorModeEnvironment.freePort();

        Outcome on = SingleOperatorModeEnvironment.launch(unreachable("true", port));

        assertThat(on.started()).as(on.output()).isFalse();
        assertThat(on.exitCode()).as(on.output()).isNotZero();
        assertThat(on.output()).contains("the database check could not run").contains(KEY).contains("ADR-0030");
        assertThat(SingleOperatorModeEnvironment.listening(on.port())).isFalse();

        Outcome off = SingleOperatorModeEnvironment.launch(unreachable("false", SingleOperatorModeEnvironment.freePort()));

        assertThat(off.started()).as(off.output()).isFalse();
        assertThat(off.output()).as("with the mode off the check does not run, so it cannot be the cause").doesNotContain("the database check could not run");
    }

    @Test
    @DisplayName("SINGLE-OP P1-17 启动审计写入失败则拒绝启动：库连接默认只读，库名与用户都满足判据；值为 true 时进程退出码非 0、无 Started、端口无监听；对照：键为 false 的同一环境正常启动")
    void p117_failedAnnouncementAuditRefusesTheStart() throws Exception {
        Outcome warmUp = SingleOperatorModeEnvironment.launch(SingleOperatorModeEnvironment.residentArguments("dev", DEV_DATABASE, DEV_USER, "false",
                0, false));
        assertThat(warmUp.started()).as("the process engine's tables exist before the read-only account starts: " + warmUp.output()).isTrue();
        Outcome control = SingleOperatorModeEnvironment.launch(SingleOperatorModeEnvironment.residentArguments("dev", DEV_DATABASE, READ_ONLY_USER, "false",
                0, false));
        assertThat(control.started()).as("the read-only account can start with the mode off, so a refusal below is the announcement's: " + control.output()).isTrue();

        int port = SingleOperatorModeEnvironment.freePort();
        Outcome on = SingleOperatorModeEnvironment.launch(SingleOperatorModeEnvironment.residentArguments("dev", DEV_DATABASE, READ_ONLY_USER, "true", port, false));

        assertThat(on.started()).as(on.output()).isFalse();
        assertThat(on.exitCode()).as(on.output()).isNotZero();
        assertThat(on.output()).doesNotContain("Started SingleOperatorModeLauncher").containsIgnoringCase("read-only");
        assertThat(SingleOperatorModeEnvironment.listening(on.port())).isFalse();
    }
}
