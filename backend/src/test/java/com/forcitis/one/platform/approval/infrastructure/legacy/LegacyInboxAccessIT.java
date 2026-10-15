package com.forcitis.one.platform.approval.infrastructure.legacy;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport.RecordingExternalConnections;
import com.forcitis.one.platform.approval.domain.LegacyInbox;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@ExtendWith(OutputCaptureExtension.class)
class LegacyInboxAccessIT extends ApprovalIntegrationSupport {

    @Autowired
    @Qualifier("legacy-approval")
    DataSource legacy;

    @Autowired
    DataSource primary;

    @Autowired
    LegacyInbox legacyInbox;

    @Autowired
    RecordingExternalConnections connections;

    static final String VIEW = "legacy_fixture.v_psa_one_legacy_todo";

    @Test
    @DisplayName("the legacy DataSource is qualified legacy-approval, opened through the integration connection port for an interactive caller, and the application's own DataSource stays the default")
    void legacyDataSourceIsSeparate() {
        assertThat(legacy).isNotSameAs(primary);
        assertThat(primary).isInstanceOfSatisfying(HikariDataSource.class, pool -> assertThat(pool.getPoolName()).isNotEqualTo("legacy-approval"));
        assertThat(connections.opened).containsExactly("legacy-approval INTERACTIVE");
    }

    @Test
    @DisplayName("ADR-0037 item 1: the read-only stand-in account reads the to-do view; writing through it fails")
    void accountReadsButCannotWrite() {
        JdbcClient asLegacy = JdbcClient.create(legacy);
        assertThat(asLegacy.sql("select count(*) from " + VIEW).query(Long.class).single()).isGreaterThanOrEqualTo(0L);
        assertThatThrownBy(() -> asLegacy.sql("delete from " + VIEW).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> asLegacy.sql("insert into legacy_fixture.todo_rows (task_id) values ('x')").update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("ADR37-1 approval keeps no check of its own that could swallow a failure: the login is checked by the integration connection factory")
    void noApprovalSideCheck() {
        assertThat(LegacyInboxConfiguration.class.getDeclaredMethods()).extracting(Method::getReturnType)
                .doesNotContain(SmartInitializingSingleton.class);
    }

    @Test
    @DisplayName("ADR-0037 item 3.5 a failed read is logged with error number and SQLState only, never the database message")
    void failedReadLogsNoMessage(CapturedOutput output) {
        LegacyInbox.LegacyTodos result = new JdbcLegacyInbox(JdbcClient.create(legacy), "legacy_fixture.no_such_view_marker_7731", null)
                .readTodos(new EmployeeId(1));

        assertThat(result.available()).isFalse();
        assertThat(output.getAll().lines().filter(line -> line.contains("no_such_view_marker_7731")))
                .as("the log line of the failed read").isNotEmpty()
                .allSatisfy(line -> assertThat(line).contains("SQLState").doesNotContain("does not exist").doesNotContain("SELECT"));
    }

    @Test
    @DisplayName("C1a-14 an unreachable old database yields legacyAvailable = false, never an exception")
    void unreachableDatabaseDegrades() {
        DriverManagerDataSource unreachable = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none", "x", "y");
        LegacyInbox.LegacyTodos result = new JdbcLegacyInbox(JdbcClient.create(unreachable), VIEW, null).readTodos(new EmployeeId(1));
        assertThat(result.available()).isFalse();
        assertThat(result.todos()).isEmpty();
    }

    @Test
    @DisplayName("without a configured url the legacy part is reported unavailable (nothing is guessed)")
    void notConfiguredIsUnavailable() {
        assertThat(new LegacyInboxConfiguration().legacyInbox(new LegacyProperties(null, null, null, null, null),
                new org.springframework.beans.factory.support.DefaultListableBeanFactory().getBeanProvider(DataSource.class))
                .readTodos(new EmployeeId(1)).available()).isFalse();
        assertThat(legacyInbox.readTodos(new EmployeeId(newId())).available()).isTrue();
    }
}
