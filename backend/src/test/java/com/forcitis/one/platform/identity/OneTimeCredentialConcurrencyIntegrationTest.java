package com.forcitis.one.platform.identity;

import com.forcitis.one.organization.workforce.api.LoginLookup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.identity.domain.SignedTicket;
import com.forcitis.one.platform.integration.api.WecomEntry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OneTimeCredentialConcurrencyIntegrationTest extends IdentityIntegrationTest {

    static final int ATTEMPTS = 16;

    private List<Integer> race(Callable<Integer> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return request.call();
            }));
        }
        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return statuses;
    }

    private void assertExactlyOneSucceeded(List<Integer> statuses) {
        assertThat(statuses).hasSize(ATTEMPTS);
        assertThat(statuses.stream().filter(s -> s == 200).count()).as("successful logins: %s", statuses).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 401).count()).as("refused replays: %s", statuses).isEqualTo(ATTEMPTS - 1);
        assertThat(count("sessions")).as("exactly one session was created").isEqualTo(1);
    }

    @Test
    @DisplayName("I-9 / C1a-03 the same signed-ticket nonce presented by many requests at once logs in exactly once")
    void signedTicketNonce() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        createAccount(1001, "employee");
        organisationSourceKnows(1001);
        jdbc.sql("""
                insert into platform_identity.ticket_issuers (ticket_issuer_id, issuer_code, signing_key_code, key_reference, is_enabled, allowed_role_codes)
                values (uuidv7(), 'km', 'km-key-1', 'env:FORCITIS_TICKET_KEY_KM', true, array['employee'])
                """).update();
        String ticket = SignedTicket.sign(KM_KEY.getBytes(StandardCharsets.UTF_8), "km-key-1", "km", 1001, "race-nonce-0123456789",
                clock.instant().minusSeconds(1), clock.instant().plusSeconds(30));

        List<Integer> statuses = race(() -> postJson(SignedTicketLoginIntegrationTest.LOGIN, "{\"ticket\":\"" + ticket + "\"}").getResponse().getStatus());

        assertExactlyOneSucceeded(statuses);
    }

    @Test
    @DisplayName("I-9 / RULE-322 the same OAuth state presented by many requests at once logs in exactly once")
    void oauthState() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("race-code", WecomEntry.WORKBENCH, 1001);
        String state = newLoginState("WORKBENCH");

        List<Integer> statuses = race(() -> postLogin(WecomLoginIntegrationTest.LOGIN,
                "{\"code\":\"race-code\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}", state).getResponse().getStatus());

        assertExactlyOneSucceeded(statuses);
    }
}
