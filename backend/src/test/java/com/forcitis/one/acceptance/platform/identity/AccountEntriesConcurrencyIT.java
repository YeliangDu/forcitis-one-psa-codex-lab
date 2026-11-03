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
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.organization.workforce.events.EmployeeRegistered;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * R-1b P2 review P2-M3 (ADR-0033 section 3 item 1): every way an account is opened inserts on the employee and reads back by the
 * employee (INSERT … ON CONFLICT (employee_id) DO NOTHING), so the WeCom login, the EmployeeRegistered handler and the operations command
 * grant-role, started for the same new employee at the same moment, end with one account and one AccountOpened, and none of them fails on
 * the unique constraint. The event is published the way the workforce module publishes it (inside a transaction, delivered after the
 * commit through the event publication registry); a handler that failed would leave its publication incomplete.
 *
 * <p>#934: the fourth entry, the OrgSnapshotChanged handler, went with the old sync. Account opening on EmployeeHired is card #1015
 * with its own acceptance (HireOpensAccountIT), so no fourth entry is pinned here.
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

    @Autowired
    EventEnvelopes envelopes;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate transactions;

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

    private UUID publishEmployeeRegistered(UUID employee) {
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.login-registration")) {
            return transactions.execute(status -> {
                EmployeeRegistered event = new EmployeeRegistered(envelopes.wrap(new EventDraft("organization.workforce.EmployeeRegistered", 1, "Employee",
                        employee, 1, null, null, List.of())), employeeIdOf(employee));
                publisher.publishEvent(event);
                return event.envelope().eventId();
            });
        }
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

    /** "COMPLETED", "FAILED" or "OPEN" for the publication of the event to the identity handler, open or archived. */
    private String deliveryToIdentity(UUID eventId, String handler) throws Exception {
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement select = registry.prepareStatement("select completion_date is not null, status from " + table
                        + " where listener_id like ? and serialized_event like ?")) {
                    select.setString(1, "%" + handler + "%");
                    select.setString(2, "%" + eventId + "%");
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            return "FAILED".equals(rs.getString(2)) ? "FAILED" : rs.getBoolean(1) ? "COMPLETED" : "OPEN";
                        }
                    }
                }
            }
        }
        return "OPEN";
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
    @DisplayName("P2-M3 ADR-0033 第 3 项第 1 条：同一新员工同时经企业微信登录、EmployeeRegistered、运维 grant-role --employee-id 三个入口建账号 → 只有一个账号、恰一条 AccountOpened、三个入口都成功、没有唯一约束冲突")
    void everyEntryAtOnceOpensOneAccount() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                UUID person = newEmployeeId();
                employee(person, unit, null);
                String code = wecomCodeFor(person);
                int mark = errors.list.size();
                CountDownLatch start = new CountDownLatch(1);
                Future<SignedIn> login = pool.submit(waiting(start, () -> signInWith(code, person)));
                Future<UUID> registered = pool.submit(waiting(start, () -> publishEmployeeRegistered(person)));
                Future<String> granted = pool.submit(waiting(start, () -> grantRoleByOperations(person)));
                start.countDown();

                UUID account = login.get(60, TimeUnit.SECONDS).userId();
                assertThat(granted.get(60, TimeUnit.SECONDS)).as("exit code and error output of grant-role").isEqualTo("0");
                UUID registeredEvent = registered.get(60, TimeUnit.SECONDS);
                Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                        .until(() -> deliveryToIdentity(registeredEvent, "HandleEmployeeRegistered"), state -> !state.equals("OPEN"));
                assertThat(deliveryToIdentity(registeredEvent, "HandleEmployeeRegistered")).as("delivery to HandleEmployeeRegistered").isEqualTo("COMPLETED");

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
