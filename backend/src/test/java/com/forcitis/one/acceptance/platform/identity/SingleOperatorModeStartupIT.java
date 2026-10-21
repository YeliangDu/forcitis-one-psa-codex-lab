package com.forcitis.one.acceptance.platform.identity;

import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.DEV_DATABASE;
import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.DEV_USER;
import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.FRESH_DATABASE;
import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.KEY;
import static com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.PROD_DATABASE;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The database check and the start-up order of the single operator mode, observed only through the child process: exit code, output, rows, open port. */
class SingleOperatorModeStartupIT {

    static final String ENABLED_ACTION = "platform.identity.single-operator-mode.enabled";

    @BeforeAll
    static void container() {
        SingleOperatorModeEnvironment.prepare();
    }

    private static Outcome resident(String database, String user, String mode) throws Exception {
        return SingleOperatorModeEnvironment.launch(SingleOperatorModeEnvironment.residentArguments("dev", database, user, mode, SingleOperatorModeEnvironment.freePort(), false));
    }

    /** For a start that is expected to succeed and is not asked about its port: the system picks the port, so no other process can have taken it. */
    private static Outcome residentUp(String database, String user, String mode) throws Exception {
        return SingleOperatorModeEnvironment.launch(SingleOperatorModeEnvironment.residentArguments("dev", database, user, mode, 0, false));
    }

    private static void assertRefusedByTheCheck(Outcome outcome) {
        assertThat(outcome.started()).as(outcome.output()).isFalse();
        assertThat(outcome.exitCode()).as(outcome.output()).isNotZero();
        assertThat(outcome.output()).contains(KEY).contains("ADR-0030").contains("the value true needs the database forcitis_one_dev and a session user whose name starts with one_dev_");
    }

    static Stream<String> wrongDatabases() {
        return Stream.concat(Stream.of(PROD_DATABASE), SingleOperatorModeEnvironment.OTHER_DATABASES.stream());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("wrongDatabases")
    @DisplayName("SINGLE-OP P1-11 值为 true 时库名不是 forcitis_one_dev 即启动失败（逐库）")
    void p111_wrongDatabaseRefuses(String database) throws Exception {
        assertRefusedByTheCheck(resident(database, DEV_USER, "true"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("wrongUsers")
    @DisplayName("SINGLE-OP P1-12 值为 true 时会话用户不以 one_dev_ 开头即启动失败（逐用户；库名正确）")
    void p112_wrongUserRefuses(String user) throws Exception {
        assertRefusedByTheCheck(resident(DEV_DATABASE, user, "true"));
    }

    static Stream<String> wrongUsers() {
        return SingleOperatorModeEnvironment.OTHER_USERS.stream();
    }

    @Test
    @DisplayName("SINGLE-OP P1-13 库名与会话用户都满足时启动通过")
    void p113_rightDatabaseAndUserStarts() throws Exception {
        Outcome outcome = residentUp(DEV_DATABASE, DEV_USER, "true");

        assertThat(outcome.started()).as(outcome.output()).isTrue();
    }

    @Test
    @DisplayName("SINGLE-OP P1-13 模式关闭（键为 false 或未设置）时不做这两项检查：库名 forcitis_one_prod 的环境照常启动")
    void p113_modeOffDoesNotCheckTheDatabase() throws Exception {
        for (String mode : new String[] {"false", null}) {
            Outcome outcome = residentUp(PROD_DATABASE, DEV_USER, mode);

            assertThat(outcome.started()).as("mode=%s: %s", mode, outcome.output()).isTrue();
        }
        Outcome otherUser = residentUp(DEV_DATABASE, SingleOperatorModeEnvironment.OTHER_USERS.getFirst(), "false");
        assertThat(otherUser.started()).as(otherUser.output()).isTrue();
    }

    @Test
    @DisplayName("SINGLE-OP P1-14 检查先于任何 ApplicationRunner：identity-operations+dev、值为 true、库名 forcitis_one_prod，子进程退出码非 0，授予、应急账号、审计与引擎表的数量与启动前相同，运维命令未执行")
    void p114_checkComesBeforeTheOperationsCommand() throws Exception {
        assertThat(SingleOperatorModeEnvironment.engineTables(FRESH_DATABASE)).as("a database no application has started on").isZero();
        List<Long> before = SingleOperatorModeEnvironment.footprint(FRESH_DATABASE);
        List<String> arguments = new ArrayList<>(SingleOperatorModeEnvironment.residentArguments("dev,identity-operations", FRESH_DATABASE, DEV_USER, "true",
                SingleOperatorModeEnvironment.freePort(), false));
        arguments.addAll(List.of("--operation=grant-role", "--employee-id=" + UUID.randomUUID(), "--role-code=business_admin",
                "--operator=" + UUID.randomUUID(), "--witness=" + UUID.randomUUID()));

        Outcome outcome = SingleOperatorModeEnvironment.launch(arguments);

        assertRefusedByTheCheck(outcome);
        assertThat(outcome.output()).as("the command did not run").doesNotContain("refused:").doesNotContain("wrong usage").doesNotContain("role granted")
                .doesNotContain("usage:");
        assertThat(SingleOperatorModeEnvironment.footprint(FRESH_DATABASE)).isEqualTo(before);
        assertThat(SingleOperatorModeEnvironment.engineTables(FRESH_DATABASE)).as("the process engine created no table").isZero();
    }

    @Test
    @DisplayName("SINGLE-OP P1-15 检查先于 Web 服务器接受请求与调度器启动：dev、值为 true、库名错误的常驻形态，上下文刷新失败，指定端口没有任何监听，调度任务计数为 0")
    void p115_checkComesBeforeTheServerAndTheScheduler() throws Exception {
        long locksBefore = SingleOperatorModeEnvironment.count(PROD_DATABASE, "platform_scheduling.scheduler_locks", null);
        int port = SingleOperatorModeEnvironment.freePort();

        Outcome outcome = SingleOperatorModeEnvironment.launch(SingleOperatorModeEnvironment.residentArguments("dev", PROD_DATABASE, DEV_USER, "true", port, true));

        assertRefusedByTheCheck(outcome);
        assertThat(outcome.output()).doesNotContain("Tomcat started");
        assertThat(SingleOperatorModeEnvironment.listening(outcome.port())).as("nothing listens on port %d", outcome.port()).isFalse();
        assertThat(SingleOperatorModeEnvironment.count(PROD_DATABASE, "platform_scheduling.scheduler_locks", null)).as("no scheduled job took a lock").isEqualTo(locksBefore)
                .isZero();
    }

    @Test
    @DisplayName("SINGLE-OP P1-16 模式开启时每次启动写一行固定文本的 WARN 日志，含激活 profile、数据库名与「ADR-0030」")
    void p116_warnLineWhenOn() throws Exception {
        for (int start = 1; start <= 2; start++) {
            Outcome outcome = residentUp(DEV_DATABASE, DEV_USER, "true");

            assertThat(outcome.started()).as(outcome.output()).isTrue();
            List<String> warnings = outcome.lines().stream().filter(line -> line.contains("ADR-0030")).toList();
            assertThat(warnings).as("start %d: one line", start).hasSize(1);
            assertThat(warnings.getFirst()).contains("WARN").contains(DEV_DATABASE).contains("dev");
        }
    }

    @Test
    @DisplayName("SINGLE-OP P1-16 模式关闭时没有这行（键为 false 与未设置）")
    void p116_noWarnLineWhenOff() throws Exception {
        for (String mode : new String[] {"false", null}) {
            Outcome outcome = residentUp(DEV_DATABASE, DEV_USER, mode);

            assertThat(outcome.started()).as(outcome.output()).isTrue();
            assertThat(outcome.output()).doesNotContain("ADR-0030");
        }
    }

    @Test
    @DisplayName("SINGLE-OP P1-17 模式开启时每次启动写一条审计 platform.identity.single-operator-mode.enabled，actor 为空，detail 为 profile=dev；连续启动两次得两条")
    void p117_auditEntryPerStartWhenOn() throws Exception {
        long before = enabledEntries(null);

        for (int start = 1; start <= 2; start++) {
            Outcome outcome = residentUp(DEV_DATABASE, DEV_USER, "true");

            assertThat(outcome.started()).as(outcome.output()).isTrue();
            assertThat(enabledEntries(null)).as("after start %d", start).isEqualTo(before + start);
        }
        assertThat(enabledEntries("actor_id is null and detail = 'profile=dev'")).as("actor empty, detail names the profile only").isEqualTo(enabledEntries(null));
    }

    @Test
    @DisplayName("SINGLE-OP P1-17 模式关闭不写（键为 false 与未设置）")
    void p117_noAuditEntryWhenOff() throws Exception {
        long before = enabledEntries(null);

        for (String mode : new String[] {"false", null}) {
            Outcome outcome = residentUp(DEV_DATABASE, DEV_USER, mode);

            assertThat(outcome.started()).as(outcome.output()).isTrue();
        }
        assertThat(enabledEntries(null)).isEqualTo(before);
    }

    @Test
    @DisplayName("SINGLE-OP P1-12 判据用 session_user 而非 current_user：以 one_prod_app 登录并 SET ROLE 为 one_dev_x，库名正确，启动仍被拒")
    void p112_sessionUserNotCurrentUser() throws Exception {
        List<String> arguments = new ArrayList<>(SingleOperatorModeEnvironment.residentArguments("dev", DEV_DATABASE, "one_prod_app", "true",
                SingleOperatorModeEnvironment.freePort(), false));
        arguments.replaceAll(argument -> argument.startsWith("--spring.datasource.url=") ? argument + "&options=-c%20role=" + SingleOperatorModeEnvironment.ROLE_TARGET : argument);

        assertRefusedByTheCheck(SingleOperatorModeEnvironment.launch(arguments));
    }

    @Test
    @DisplayName("SINGLE-OP P1-13 失败关闭：值为 true 而数据库不可达时启动失败，不是放行，端口没有监听")
    void p113_unreachableDatabaseFailsClosed() throws Exception {
        int port = SingleOperatorModeEnvironment.freePort();
        List<String> arguments = new ArrayList<>(SingleOperatorModeEnvironment.residentArguments("dev", DEV_DATABASE, DEV_USER, "true", port, false));
        int unreachable = SingleOperatorModeEnvironment.freePort();
        arguments.replaceAll(argument -> argument.startsWith("--spring.datasource.url=")
                ? "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + unreachable + "/" + DEV_DATABASE + "?sslmode=require&connectTimeout=3" : argument);
        arguments.add("--spring.datasource.hikari.connection-timeout=3000");

        Outcome outcome = SingleOperatorModeEnvironment.launch(arguments);

        assertThat(outcome.started()).as(outcome.output()).isFalse();
        assertThat(outcome.exitCode()).as(outcome.output()).isNotZero();
        assertThat(SingleOperatorModeEnvironment.listening(outcome.port())).isFalse();
    }

    @Test
    @DisplayName("SINGLE-OP P1-06 监听器先于迁移步骤：--spring.profiles.active=migrate,prod 带键（false）在迁移前失败，退出码非 0，库内无迁移历史；对照：migrate,dev 不带键则迁移成功")
    void p106_listenerRunsBeforeTheMigrateStep() throws Exception {
        String database = SingleOperatorModeEnvironment.PROBE_DATABASE;
        List<String> refused = List.of("--spring.profiles.active=migrate,prod", "--spring.datasource.url=" + SingleOperatorModeEnvironment.url(database),
                "--spring.datasource.username=" + DEV_USER, "--spring.datasource.password=" + SingleOperatorModeEnvironment.PASSWORD, "--" + KEY + "=false");
        assertThat(migrationTables(database)).as("before").isZero();

        Outcome outcome = SingleOperatorModeEnvironment.launch(refused);

        assertThat(outcome.exitCode()).as(outcome.output()).isNotZero();
        assertThat(outcome.output()).contains("the key must not be set while the profile prod is active").doesNotContain("MIGRATE-ONLY");
        assertThat(migrationTables(database)).as("nothing was migrated").isZero();

        Outcome control = SingleOperatorModeEnvironment.launch(List.of("--spring.profiles.active=migrate,dev", "--spring.datasource.url=" + SingleOperatorModeEnvironment.url(database),
                "--spring.datasource.username=" + DEV_USER, "--spring.datasource.password=" + SingleOperatorModeEnvironment.PASSWORD));
        assertThat(control.exitCode()).as(control.output()).isZero();
        assertThat(migrationTables(database)).as("the control did migrate").isPositive();
    }

    private static long migrationTables(String database) throws Exception {
        try (var connection = SingleOperatorModeEnvironment.connect(database, DEV_USER);
             var rs = connection.createStatement().executeQuery("select count(*) from information_schema.tables where table_name = 'flyway_schema_history'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long enabledEntries(String also) throws Exception {
        return SingleOperatorModeEnvironment.count(DEV_DATABASE, "platform_audit.audit_entries", "action = '" + ENABLED_ACTION + "'" + (also == null ? "" : " and " + also));
    }
}
