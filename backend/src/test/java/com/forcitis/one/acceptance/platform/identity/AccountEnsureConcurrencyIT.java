package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 (ADR-0033 section 3 item 1): an account is opened by inserting on the employee and reading back by the employee
 * (INSERT … ON CONFLICT (employee_id) DO NOTHING), so two first logins of the same person at the same moment end with one account, and
 * both logins answer that account. The account id is identity's own UUID, not the employee id.
 */
class AccountEnsureConcurrencyIT extends AccountsAcceptanceSupport {

    static final int PEOPLE = 5;

    static final TestDatabase postgres = TestPostgres.database(AccountEnsureConcurrencyIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    // RULE-418
    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 1 条：同一员工并发两次首次登录（ensureAccount 并发）只得到一个账号，两次登录答出同一个 userId，且 userId ≠ 员工 ID")
    void twoFirstLoginsAtOnceOpenOneAccount() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < PEOPLE; i++) {
                UUID person = newEmployeeId();
                employee(person, unit, null);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<SignedIn>> logins = new ArrayList<>();
                for (String code : List.of(wecomCodeFor(person), wecomCodeFor(person))) {
                    Callable<SignedIn> login = () -> {
                        start.await();
                        return signInWith(code, person);
                    };
                    logins.add(pool.submit(login));
                }
                start.countDown();
                List<UUID> answered = new ArrayList<>();
                for (Future<SignedIn> each : logins) {
                    answered.add(each.get(30, TimeUnit.SECONDS).userId());
                }

                assertThat(answered.get(0)).as("both logins of employee %s answer the same account", person).isEqualTo(answered.get(1));
                assertThat(accountsOfEmployee(person)).as("accounts of employee %s", person).containsExactly(answered.get(0));
                assertThat(answered.get(0).toString()).isNotEqualTo(text(person));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
