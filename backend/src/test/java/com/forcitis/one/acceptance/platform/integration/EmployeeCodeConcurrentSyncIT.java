package com.forcitis.one.acceptance.platform.integration;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.application.SyncResult;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR-0078: the employee code is globally unique (database key uk_employees_employee_code). When another transaction takes the code of a
 * newcomer after the sync has read the snapshot and before it writes (same code, concurrent), the database key fails the sync write: the run
 * ends FAILED (not SUCCEEDED, not swallowed), no half-built newcomer stays, and nothing is thrown to the caller. Black-box: a second JDBC
 * connection holds an uncommitted row with the code, the sync runs on its own thread, and once that thread waits on the key the row is committed.
 */
class EmployeeCodeConcurrentSyncIT extends OrgSourceWorldSupport {

    static final TestDatabase postgres = TestPostgres.database(EmployeeCodeConcurrentSyncIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final long UNIT = 9300;
    static final String UNIT_PATH = ROOT_PATH + "0031";
    static final long RACE_PERSON = 9301;
    static final long CONTROL_PERSON = 9302;
    static final long WAIT_SECONDS = 60;

    @Test
    @DisplayName("ADR-0078：同号并发——抢先事务提交了新人的工号，同步写库因唯一键失败，该次同步以 FAILED 结束、不抛异常、库中该工号只剩抢先的那一行")
    void aCodeTakenBetweenReadAndWriteFailsTheRun() throws Exception {
        String code = "CONC-" + UUID.randomUUID().toString().substring(0, 8);
        unit(UNIT, "C9300", "Concurrent unit", UNIT_PATH);
        member(RACE_PERSON, "Racing newcomer", code, UNIT, UNIT_PATH, BASE_POST, null, "wx-world-9301", "world-9301");
        UUID rival = UUID.randomUUID();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Connection rivalConnection = schemaConnection("organization_workforce");
        boolean committed = false;
        try {
            rivalConnection.setAutoCommit(false);
            try (PreparedStatement insert = rivalConnection.prepareStatement(
                    "insert into employees (id, employee_code, display_name, employment_status) values (?::uuid, ?, 'Rival holder', 'EMPLOYED')")) {
                insert.setString(1, rival.toString());
                insert.setString(2, code);
                insert.executeUpdate();
            }

            Future<SyncResult> run = executor.submit(this::runSync);
            awaitSomeoneWaitingOnALock(run);

            rivalConnection.commit();
            committed = true;

            SyncResult result = run.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(result.status().name()).as("the run ends FAILED: %s", result.note()).isEqualTo("FAILED");
        } catch (TimeoutException e) {
            fail("the sync did not finish within " + WAIT_SECONDS + " seconds after the rival committed");
        } finally {
            if (!committed) {
                rivalConnection.rollback();
            }
            rivalConnection.close();
            executor.shutdownNow();
            // keep the other case clean: the racing person must not stay in the source for the next sync
            World.run(World.A8_WORLD, "DELETE dbo.member_rows WHERE ID = " + RACE_PERSON);
        }

        assertThat(employeesWithCode(code)).as("only the rival row holds the code, no half-built newcomer").isEqualTo(1);
        assertThat(displayNameOfCode(code)).isEqualTo("Rival holder");
        assertThat(employeesNamed("Racing newcomer")).as("no row of the newcomer under any code").isZero();
    }

    @Test
    @DisplayName("对照：没有抢先事务时，同一类新人正常以 SUCCEEDED 建档，工号落库")
    void withoutARivalTheNewcomerIsBuilt() throws Exception {
        String code = "CONC-" + UUID.randomUUID().toString().substring(0, 8);
        unit(UNIT + 10, "C9310", "Control unit", ROOT_PATH + "0032");
        member(CONTROL_PERSON, "Control newcomer", code, UNIT + 10, ROOT_PATH + "0032", BASE_POST, null, "wx-world-9302", "world-9302");

        SyncResult result = runSync();

        assertThat(result.status().name()).as("note: %s", result.note()).isEqualTo("SUCCEEDED");
        assertThat(employeesWithCode(code)).isEqualTo(1);
        assertThat(displayNameOfCode(code)).isEqualTo("Control newcomer");
    }

    private SyncResult runSync() {
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            return sync.execute(SyncTrigger.MANUAL, false);
        }
    }

    /** Polls until a backend of this database waits on a lock (the sync insert waiting on the rival's uncommitted key); fails on timeout or if the sync ended early. */
    private void awaitSomeoneWaitingOnALock(Future<SyncResult> run) throws Exception {
        try (Connection watcher = schemaConnection("organization_workforce"); PreparedStatement query = watcher.prepareStatement(
                "select count(*) from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid() and wait_event_type = 'Lock'")) {
            await("the sync waits on the rival's uncommitted code within " + WAIT_SECONDS + " seconds")
                    .atMost(Duration.ofSeconds(WAIT_SECONDS)).pollInterval(Duration.ofMillis(50))
                    .until(() -> {
                        if (run.isDone()) {
                            fail("the sync ended before it waited on the rival's uncommitted code: " + run.get().status() + " " + run.get().note());
                        }
                        try (ResultSet rs = query.executeQuery()) {
                            rs.next();
                            return rs.getInt(1) > 0;
                        }
                    });
        }
    }

    private int employeesWithCode(String code) throws SQLException {
        return count("select count(*) from employees where employee_code = ?", code);
    }

    private int employeesNamed(String name) throws SQLException {
        return count("select count(*) from employees where display_name = ?", name);
    }

    private int count(String sql, String parameter) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement select = workforce.prepareStatement(sql)) {
            select.setString(1, parameter);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String displayNameOfCode(String code) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement select = workforce.prepareStatement(
                "select display_name from employees where employee_code = ?")) {
            select.setString(1, code);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
