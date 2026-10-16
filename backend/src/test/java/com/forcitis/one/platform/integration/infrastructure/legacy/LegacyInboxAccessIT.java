package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport.RecordingExternalConnections;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.LegacyInbox;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.application.InMemorySourceReferences;
import com.forcitis.one.platform.integration.application.SourceReferences;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@ExtendWith(OutputCaptureExtension.class)
class LegacyInboxAccessIT extends ApprovalIntegrationSupport {

    @Autowired
    LegacyInboxConfiguration.InboxConnection inboxConnection;

    @Autowired
    DataSource primary;

    @Autowired
    ApplicationContext context;

    @Autowired
    LegacyInbox legacyInbox;

    @Autowired
    SourceReferences sourceReferences;

    @Autowired
    BusinessZone zone;

    @Autowired
    Clock clock;

    @Autowired
    RecordingExternalConnections connections;

    static final String VIEW = "legacy_fixture.v_psa_one_legacy_todo";

    /** References in which test employee 1 is the old system's person 1, so a read reaches the database. */
    static InMemorySourceReferences employeeOneKnown() {
        return new InMemorySourceReferences().confirm(ExternalSource.LEGACY_SYSTEM, SourceObjectKind.EMPLOYEE, 1, TestAccounts.employee(1).value());
    }

    @Test
    @DisplayName("the to-do connection is no DataSource bean: it is opened through the integration connection port for an interactive caller, and the application's own DataSource stays the only one")
    void legacyConnectionIsSeparate() {
        assertThat(context.getBeanNamesForType(DataSource.class)).hasSize(1);
        assertThat(primary).isInstanceOfSatisfying(HikariDataSource.class,
                pool -> assertThat(pool.getPoolName()).isNotEqualTo(LegacyInboxConfiguration.CONNECTION));
        assertThat(connections.opened).containsExactly("integration-legacy-inbox INTERACTIVE");
    }

    @Test
    @DisplayName("ADR-0037 item 1: the read-only stand-in account reads the to-do view; writing through it fails")
    void accountReadsButCannotWrite() {
        JdbcClient asLegacy = inboxConnection.jdbc();
        assertThat(asLegacy.sql("select count(*) from " + VIEW).query(Long.class).single()).isGreaterThanOrEqualTo(0L);
        assertThatThrownBy(() -> asLegacy.sql("delete from " + VIEW).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> asLegacy.sql("insert into legacy_fixture.todo_rows (task_id) values ('x')").update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("ADR37-1 the inbox keeps no check of its own that could swallow a failure: the login is checked by the integration connection factory")
    void noInboxSideCheck() {
        assertThat(LegacyInboxConfiguration.class.getDeclaredMethods()).extracting(Method::getReturnType)
                .doesNotContain(SmartInitializingSingleton.class);
    }

    @Test
    @DisplayName("ADR-0037 item 3.5 a failed read is logged with error number and SQLState only, never the database message")
    void failedReadLogsNoMessage(CapturedOutput output) {
        LegacyInbox.LegacyTodos result = new JdbcLegacyInbox(inboxConnection.jdbc(), "legacy_fixture.no_such_view_marker_7731", null,
                employeeOneKnown(), zone, clock, new SimpleMeterRegistry()).readTodos(TestAccounts.employee(1));

        assertThat(result.available()).isFalse();
        assertThat(output.getAll().lines().filter(line -> line.contains("no_such_view_marker_7731")))
                .as("the log line of the failed read").isNotEmpty()
                .allSatisfy(line -> assertThat(line).contains("SQLState").doesNotContain("does not exist").doesNotContain("SELECT"));
    }

    @Test
    @DisplayName("C1a-14 an unreachable old database yields legacyAvailable = false, never an exception")
    void unreachableDatabaseDegrades() {
        DriverManagerDataSource unreachable = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none", "nobody", UUID.randomUUID().toString());
        LegacyInbox.LegacyTodos result = new JdbcLegacyInbox(JdbcClient.create(unreachable), VIEW, null, employeeOneKnown(), zone, clock, new SimpleMeterRegistry())
                .readTodos(TestAccounts.employee(1));
        assertThat(result.available()).isFalse();
        assertThat(result.todos()).isEmpty();
    }

    @Test
    @DisplayName("without a configured url the legacy part is reported unavailable (nothing is guessed); with one, an employee the old system knows is read")
    void notConfiguredIsUnavailable() {
        LegacyInbox notConnected = new LegacyInboxConfiguration().legacyInbox(
                new LegacyInboxConfiguration.LegacyInboxSettings(LegacyInboxProperties.none()),
                new DefaultListableBeanFactory().getBeanProvider(LegacyInboxConfiguration.InboxConnection.class), employeeOneKnown(), zone, clock, new SimpleMeterRegistry());
        assertThat(notConnected.readTodos(TestAccounts.employee(1)).available()).isFalse();

        EmployeeId known = new EmployeeId(sourceReferences.allocate(ExternalSource.LEGACY_SYSTEM, SourceObjectKind.EMPLOYEE, newId()));
        assertThat(legacyInbox.readTodos(known).available()).isTrue();
    }
}
