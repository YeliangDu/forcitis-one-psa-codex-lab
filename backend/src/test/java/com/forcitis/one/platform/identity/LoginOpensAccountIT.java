package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeHired;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.AccountOpeningRoute;
import com.forcitis.one.platform.identity.events.AccountOpened;
import com.forcitis.one.platform.integration.api.WecomEntry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/** #1174 (ADR-0069 section 9): an account opened by a login carries route LOGIN and raises one alert and one audit record; a hire or a login of an existing account raises none. */
class LoginOpensAccountIT extends IdentityIntegrationTest {

    static final String ACTION = "platform.identity.account.opened-at-login";

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private MvcResult wecomLogin(long id) throws Exception {
        String code = "code-" + id + "-" + UUID.randomUUID();
        wecomAccepts(code, WecomEntry.WORKBENCH, id);
        String state = newLoginState("WORKBENCH");
        return postLogin("/api/v1/platform/sessions:wecom-login", "{\"code\":\"" + code + "\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}", state);
    }

    private List<ILoggingEvent> alertsWhile(Runnable action) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        try {
            action.run();
        } finally {
            root.detachAppender(logged);
        }
        return logged.list.stream().filter(line -> line.getFormattedMessage().startsWith("ALERT platform.identity account ")).toList();
    }

    private List<AuditEntry> openedAtLogin() {
        return recorded().stream().filter(entry -> entry.action().equals(ACTION)).toList();
    }

    private WecomLoginCall login(long id) {
        return new WecomLoginCall(id);
    }

    private final class WecomLoginCall implements Runnable {
        private final long id;
        MvcResult result;

        WecomLoginCall(long id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
                result = wecomLogin(id);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    @DisplayName("#1174 an employed person without an account who logs in through WeCom gets an account whose event has route LOGIN, one audit record and one ALERT line")
    void loginOpensAccountWithRouteLogin() {
        knownEmployee(8001, EmploymentStatus.EMPLOYED);
        WecomLoginCall call = login(8001);

        List<ILoggingEvent> alerts = alertsWhile(call);

        assertThat(call.result.getResponse().getStatus()).isEqualTo(200);
        assertThat(count("user_accounts")).isEqualTo(1);
        assertThat(domainEvents.of(AccountOpened.class)).singleElement().satisfies(opened -> assertThat(opened.openingRoute()).isEqualTo(AccountOpeningRoute.LOGIN));
        assertThat(openedAtLogin()).singleElement().satisfies(entry -> {
            assertThat(entry.detail()).contains("route LOGIN").doesNotContain("Synthetic");
            assertThat(entry.loginMethod()).as("the route is no login method: the audit table admits only the six methods").isNull();
        });
        assertThat(alerts).singleElement().satisfies(line -> assertThat(line.getFormattedMessage()).contains(accountOf(8001).toString()));
    }

    @Test
    @DisplayName("#1174 a login of an existing account opens nothing and raises neither the audit record nor the ALERT line")
    void loginOfAnExistingAccountRaisesNothing() {
        knownEmployee(8002, EmploymentStatus.EMPLOYED);
        createAccount(8002, "employee");
        WecomLoginCall call = login(8002);

        List<ILoggingEvent> alerts = alertsWhile(call);

        assertThat(call.result.getResponse().getStatus()).isEqualTo(200);
        assertThat(domainEvents.of(AccountOpened.class)).isEmpty();
        assertThat(openedAtLogin()).isEmpty();
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("#1174 a separated person is refused with PLATFORM_IDENTITY_EMPLOYEE_SEPARATED and gets no account, event, audit record or ALERT line")
    void separatedPersonGetsNone() throws Exception {
        knownEmployee(8003, EmploymentStatus.SEPARATED);
        WecomLoginCall call = login(8003);

        List<ILoggingEvent> alerts = alertsWhile(call);

        assertThat(call.result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(call.result).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(count("user_accounts")).isZero();
        assertThat(domainEvents.of(AccountOpened.class)).isEmpty();
        assertThat(openedAtLogin()).isEmpty();
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("#1174 six logins of one employed person without an account at once open exactly one account: one event, one audit record, one ALERT line")
    void concurrentLoginsRaiseTheAlertOnce() throws Exception {
        int attempts = 6;
        knownEmployee(8005, EmploymentStatus.EMPLOYED);
        List<String[]> prepared = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String code = "race-" + i + "-" + UUID.randomUUID();
            wecomAccepts(code, WecomEntry.WORKBENCH, 8005);
            prepared.add(new String[] {code, newLoginState("WORKBENCH")});
        }
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String[] login : prepared) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return postLogin("/api/v1/platform/sessions:wecom-login",
                            "{\"code\":\"" + login[0] + "\",\"state\":\"" + login[1] + "\",\"entry\":\"WORKBENCH\"}", login[1]).getResponse().getStatus();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdown();
            root.detachAppender(logged);
        }

        assertThat(statuses).as("every login succeeds: %s", statuses).containsOnly(200);
        assertThat(count("user_accounts")).isEqualTo(1);
        assertThat(domainEvents.of(AccountOpened.class)).hasSize(1);
        assertThat(openedAtLogin()).as("only the call that opened the account records it").hasSize(1);
        assertThat(logged.list.stream().filter(line -> line.getFormattedMessage().startsWith("ALERT platform.identity account ")).toList()).hasSize(1);
    }

    @Test
    @DisplayName("#1174 a hire opens the account with route HIRE and raises neither the audit record nor the ALERT line")
    void hireOpensAccountWithRouteHire() {
        knownEmployee(8004, EmploymentStatus.EMPLOYED);
        EmployeeId employee = TestAccounts.employee(8004);
        EmployeeHired hired = new EmployeeHired(TestEnvelopes.of("organization.workforce.EmployeeHired", "Employee", employee.value()),
                new PersonnelCommandId(UUID.randomUUID()), employee, LocalDate.of(2026, 11, 2), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));

        List<ILoggingEvent> alerts = alertsWhile(() -> {
            tx.executeWithoutResult(status -> events.publishEvent(hired));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(domainEvents.of(AccountOpened.class)).hasSize(1));
        });

        assertThat(domainEvents.of(AccountOpened.class)).singleElement().satisfies(opened -> assertThat(opened.openingRoute()).isEqualTo(AccountOpeningRoute.HIRE));
        assertThat(openedAtLogin()).isEmpty();
        assertThat(alerts).isEmpty();
    }
}
