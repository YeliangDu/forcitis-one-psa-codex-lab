package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 review P2-M3 (ADR-0033 section 3 item 1): every way an account is opened inserts on the employee and reads back by the
 * employee (INSERT … ON CONFLICT (employee_id) DO NOTHING), so the WeCom login and the operations command
 * grant-role, started for the same new employee at the same moment, end with one account and one AccountOpened, and none of them fails on
 * the unique constraint. AccountOpened is delivered after the commit through the
 * event publication registry.
 *
 * <p>#934: the OrgSnapshotChanged handler went with the old sync, and the EmployeeRegistered handler went with the A8 single sign-on (#1174).
 * Account opening on EmployeeHired is card #1015 with its own acceptance (HireOpensAccountIT), so it is not pinned here.
 */
class AccountEntriesConcurrencyIT extends AccountsAcceptanceSupport {

    static final int ROUNDS = 3;

    static final TestDatabase postgres = TestPostgres.database(AccountEntriesConcurrencyIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    OperationsCommands operations;

    private ListAppender<ILoggingEvent> errors;
    private Logger root;

    @BeforeEach
    void captureErrors() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        errors = new ListAppender<>();
        errors.start();
        root.addAppender(errors);
    }

    @AfterEach
    void releaseErrors() {
        root.detachAppender(errors);
        errors.stop();
    }

    private String grantRoleByOperations(UUID employee) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ensureAttestors();
        int exit = operations.run(new DefaultApplicationArguments("--operation=grant-role", "--employee-id=" + text(employee), "--role-code=business_admin",
                "--operator=01900000-0000-7000-8000-00000000a001", "--witness=01900000-0000-7000-8000-00000000a002"), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return (exit + " " + err.toString(StandardCharsets.UTF_8)).strip();
    }

    private int accountOpenedEvents(UUID account) throws Exception {
        List<String> eventIds = new ArrayList<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement select = registry.prepareStatement("select serialized_event from " + table + " where event_type like ? and serialized_event like ?")) {
                    select.setString(1, "%.AccountOpened");
                    select.setString(2, "%\"aggregateId\":\"" + account + "\"%");
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            String event = rs.getString(1);
                            int at = event.indexOf("\"eventId\":\"") + "\"eventId\":\"".length();
                            String id = event.substring(at, event.indexOf('"', at));
                            if (!eventIds.contains(id)) {
                                eventIds.add(id);
                            }
                        }
                    }
                }
            }
        }
        return eventIds.size();
    }

    private static boolean isConstraintFailure(ILoggingEvent event) {
        StringBuilder text = new StringBuilder(event.getFormattedMessage());
        for (IThrowableProxy thrown = event.getThrowableProxy(); thrown != null; thrown = thrown.getCause()) {
            text.append(' ').append(thrown.getClassName()).append(' ').append(thrown.getMessage());
        }
        String all = text.toString();
        return all.contains("duplicate key") || all.contains("uk_user_accounts") || all.contains("DuplicateKey") || all.contains("DataIntegrityViolation");
    }

    @Test
    @DisplayName("P2-M3 ADR-0033 第 3 项第 1 条：同一新员工同时经企业微信登录、运维 grant-role --employee-id 两个入口建账号 → 只有一个账号、恰一条 AccountOpened、两个入口都成功、没有唯一约束冲突")
    void everyEntryAtOnceOpensOneAccount() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                UUID person = newEmployeeId();
                employee(person, unit, null);
                String code = wecomCodeFor(person);
                int mark = errors.list.size();
                CountDownLatch start = new CountDownLatch(1);
                Future<SignedIn> login = pool.submit(waiting(start, () -> signInWith(code, person)));
                Future<String> granted = pool.submit(waiting(start, () -> grantRoleByOperations(person)));
                start.countDown();

                UUID account = login.get(60, TimeUnit.SECONDS).userId();
                assertThat(granted.get(60, TimeUnit.SECONDS)).as("exit code and error output of grant-role").isEqualTo("0");

                assertThat(accountsOfEmployee(person)).as("accounts of employee %s", person).containsExactly(account);
                assertThat(account.toString()).isNotEqualTo(text(person));
                Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> accountOpenedEvents(account) >= 1);
                assertThat(accountOpenedEvents(account)).as("AccountOpened events of %s", account).isEqualTo(1);
                assertThat(List.copyOf(errors.list).subList(mark, errors.list.size()).stream()
                        .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).filter(AccountEntriesConcurrencyIT::isConstraintFailure)
                        .map(ILoggingEvent::getFormattedMessage).toList()).as("unique constraint failures logged").isEmpty();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> Callable<T> waiting(CountDownLatch start, Callable<T> work) {
        return () -> {
            start.await();
            return work.call();
        };
    }
}
