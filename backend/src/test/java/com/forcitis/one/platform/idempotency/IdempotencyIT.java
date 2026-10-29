package com.forcitis.one.platform.idempotency;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.IdempotentResponses;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.web.ProbeIdempotencyScope;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Idempotency-Key: the same user, key and request get the first answer again for 24 hours; nothing that hands out a credential is stored. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
@Import({IdempotencyIT.Probe.class, ProbeIdempotencyScope.class})
class IdempotencyIT {
    static final String ORDERS = "/api/v1/platform/idempotency-probe/orders";
    static final String SESSIONS = "/api/v1/platform/idempotency-probe/sessions:echo";
    static final String KEY = "Idempotency-Key";

    static final String SIZED = "/api/v1/platform/idempotency-probe/sized";
    static final String UNLISTED = "/api/v1/platform/idempotency-probe/unlisted";

    @RestController
    @RequestMapping("/api/v1/platform/idempotency-probe")
    static class Probe {
        final AtomicInteger runs = new AtomicInteger();
        volatile CountDownLatch entered = new CountDownLatch(0);
        volatile CountDownLatch proceed = new CountDownLatch(0);
        volatile boolean failNext;

        @PostMapping("/orders")
        ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> request) throws InterruptedException {
            int run = runs.incrementAndGet();
            entered.countDown();
            if (!proceed.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the request");
            }
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("the first attempt fails");
            }
            return ResponseEntity.created(java.net.URI.create("/api/v1/platform/idempotency-probe/orders/" + run)).eTag("\"" + run + "\"")
                    .body(Map.of("run", run, "echo", request));
        }

        @PostMapping("/sized")
        ResponseEntity<String> sized(@RequestParam int length) {
            runs.incrementAndGet();
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("x".repeat(length));
        }

        @PostMapping("/unlisted")
        Map<String, Object> unlisted() {
            return Map.of("run", runs.incrementAndGet());
        }

        @PostMapping("/sessions:echo")
        Map<String, Object> hands() {
            return Map.of("run", runs.incrementAndGet(), "credential", "handed-out-" + runs.get());
        }

        @PutMapping("/orders/1")
        Map<String, Object> replace() {
            return Map.of("run", runs.incrementAndGet());
        }
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(IdempotencyIT.class, TestPostgres.MIGRATED_TESTDATA).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    Probe probe;

    @Autowired
    IdempotentResponses responses;

    @Autowired
    MeterRegistry meters;

    @BeforeEach
    void reset() {
        jdbc.sql("delete from platform_idempotency.idempotent_responses").update();
        probe.runs.set(0);
        probe.entered = new CountDownLatch(0);
        probe.proceed = new CountDownLatch(0);
        probe.failNext = false;
    }

    private MockHttpServletRequestBuilder order(String user, String key, String body) {
        return post(ORDERS).with(user(TestAccounts.account(Long.parseLong(user)).value().toString())).with(csrf()).header(KEY, key).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String problemCode(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("errorCode").asString();
    }

    @Test
    @DisplayName("the same key and request again: the handler does not run again, and status, body, ETag and Location are those of the first answer")
    void replay() throws Exception {
        MvcResult first = mvc.perform(order("1", "k-replay", "{\"a\":1}")).andReturn();
        MvcResult second = mvc.perform(order("1", "k-replay", "{\"a\":1}")).andReturn();

        assertThat(probe.runs.get()).isEqualTo(1);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(second.getResponse().getHeader("ETag")).isEqualTo("\"1\"");
        assertThat(second.getResponse().getHeader("Location")).endsWith("/orders/1");
        assertThat(second.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
    }

    @Test
    @DisplayName("the same key with another request is refused with 422 and runs nothing")
    void sameKeyOtherRequest() throws Exception {
        mvc.perform(order("1", "k-422", "{\"a\":1}")).andReturn();

        MvcResult other = mvc.perform(order("1", "k-422", "{\"a\":2}")).andReturn();

        assertThat(other.getResponse().getStatus()).isEqualTo(422);
        assertThat(problemCode(other)).isEqualTo("PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED");
        assertThat(probe.runs.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("while the first request still runs, the same key answers 409; when it is done, the key replays its answer")
    void concurrentRequest() throws Exception {
        probe.entered = new CountDownLatch(1);
        probe.proceed = new CountDownLatch(1);
        CompletableFuture<MvcResult> first = CompletableFuture.supplyAsync(() -> {
            try {
                return mvc.perform(order("1", "k-race", "{\"a\":1}")).andReturn();
            } catch (Exception failed) {
                throw new IllegalStateException(failed);
            }
        });
        assertThat(probe.entered.await(20, TimeUnit.SECONDS)).isTrue();

        MvcResult during = mvc.perform(order("1", "k-race", "{\"a\":1}")).andReturn();
        probe.proceed.countDown();
        MvcResult done = first.get(20, TimeUnit.SECONDS);
        MvcResult after = mvc.perform(order("1", "k-race", "{\"a\":1}")).andReturn();

        assertThat(during.getResponse().getStatus()).isEqualTo(409);
        assertThat(problemCode(during)).isEqualTo("PLATFORM_WEB_IDEMPOTENCY_IN_PROGRESS");
        assertThat(done.getResponse().getStatus()).isEqualTo(201);
        assertThat(after.getResponse().getContentAsString()).isEqualTo(done.getResponse().getContentAsString());
        assertThat(probe.runs.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a key belongs to the user who sent it: another user with the same key runs the request")
    void keysArePerUser() throws Exception {
        mvc.perform(order("1", "k-shared", "{\"a\":1}")).andReturn();

        MvcResult other = mvc.perform(order("2", "k-shared", "{\"a\":1}")).andReturn();

        assertThat(other.getResponse().getStatus()).isEqualTo(201);
        assertThat(other.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(probe.runs.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("an answer of 5xx is not kept: the retry with the same key runs again")
    void serverErrorIsRetried() throws Exception {
        probe.proceed = new CountDownLatch(0);
        probe.failNext = true;

        MvcResult failed = mvc.perform(order("1", "k-5xx", "{\"a\":1}")).andReturn();
        MvcResult retried = mvc.perform(order("1", "k-5xx", "{\"a\":1}")).andReturn();

        assertThat(failed.getResponse().getStatus()).isEqualTo(500);
        assertThat(retried.getResponse().getStatus()).isEqualTo(201);
        assertThat(probe.runs.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("after 24 hours the key is free again, and the cleanup removes the expired answer")
    void expiry() throws Exception {
        mvc.perform(order("1", "k-old", "{\"a\":1}")).andReturn();
        jdbc.sql("update platform_idempotency.idempotent_responses set expires_at = :past").param("past", Timestamp.from(Instant.now().minusSeconds(60))).update();

        MvcResult again = mvc.perform(order("1", "k-old", "{\"a\":1}")).andReturn();
        assertThat(again.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(probe.runs.get()).isEqualTo(2);

        jdbc.sql("update platform_idempotency.idempotent_responses set expires_at = :past").param("past", Timestamp.from(Instant.now().minusSeconds(60))).update();
        assertThat(responses.purgeExpired(Instant.now())).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from platform_idempotency.idempotent_responses").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("a claim whose request never finished is taken over by a retry after the in-progress timeout, and the retry is then 'in progress' for others")
    void abandonedClaimIsReclaimed() throws Exception {
        mvc.perform(order("1", "k-dead", "{\"a\":1}")).andReturn();
        jdbc.sql("""
                update platform_idempotency.idempotent_responses set response_number = null, response_name = null, response_text = null, response_header_text = null, updated_at = :old
                """).param("old", Timestamp.from(Instant.now().minus(Duration.ofMinutes(10)))).update();
        probe.runs.set(0);

        probe.entered = new CountDownLatch(1);
        probe.proceed = new CountDownLatch(1);
        CompletableFuture<MvcResult> retry = CompletableFuture.supplyAsync(() -> {
            try {
                return mvc.perform(order("1", "k-dead", "{\"a\":1}")).andReturn();
            } catch (Exception failed) {
                throw new IllegalStateException(failed);
            }
        });
        assertThat(probe.entered.await(20, TimeUnit.SECONDS)).isTrue();
        MvcResult third = mvc.perform(order("1", "k-dead", "{\"a\":1}")).andReturn();
        probe.proceed.countDown();

        assertThat(retry.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
        assertThat(third.getResponse().getStatus()).isEqualTo(409);
        assertThat(probe.runs.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("what hands out a credential (under /sessions) is never stored, and only POST is covered")
    void scope() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(SESSIONS).with(user(TestAccounts.account(1).value().toString())).with(csrf()).header(KEY, "k-session")).andReturn();
            mvc.perform(put("/api/v1/platform/idempotency-probe/orders/1").with(user(TestAccounts.account(1).value().toString())).with(csrf()).header(KEY, "k-put")).andReturn();
        }

        assertThat(probe.runs.get()).as("every call ran").isEqualTo(4);
        assertThat(jdbc.sql("select count(*) from platform_idempotency.idempotent_responses").query(Long.class).single()).isZero();
    }

    private MockHttpServletRequestBuilder sized(String key, int length) {
        return post(SIZED).queryParam("length", String.valueOf(length)).with(user(TestAccounts.account(1).value().toString())).with(csrf()).header(KEY, key);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: an answer of exactly 32768 characters is stored and replayed; one character more is refused by the limit and not stored")
    void answerAtTheLimitIsStored() throws Exception {
        mvc.perform(sized("k-limit", IdempotentResponses.MAX_RESPONSE_LENGTH)).andReturn();
        MvcResult replayed = mvc.perform(sized("k-limit", IdempotentResponses.MAX_RESPONSE_LENGTH)).andReturn();

        assertThat(probe.runs.get()).isEqualTo(1);
        assertThat(replayed.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replayed.getResponse().getContentAsString()).hasSize(IdempotentResponses.MAX_RESPONSE_LENGTH);
        assertThat(jdbc.sql("select char_length(response_text) from platform_idempotency.idempotent_responses").query(Integer.class).single())
                .isEqualTo(IdempotentResponses.MAX_RESPONSE_LENGTH);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: an answer over the limit is not stored, the caller still gets it, the key stays in progress (409 on retry, no second run) and the count rises")
    void oversizeAnswerIsNotStoredAndTheKeyStaysHeld() throws Exception {
        double before = meters.counter("forcitis.platform.web.idempotency.response.oversize").count();

        MvcResult first = mvc.perform(sized("k-big", IdempotentResponses.MAX_RESPONSE_LENGTH + 1)).andReturn();
        MvcResult retry = mvc.perform(sized("k-big", IdempotentResponses.MAX_RESPONSE_LENGTH + 1)).andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(first.getResponse().getContentAsString()).hasSize(IdempotentResponses.MAX_RESPONSE_LENGTH + 1);
        assertThat(retry.getResponse().getStatus()).isEqualTo(409);
        assertThat(problemCode(retry)).isEqualTo("PLATFORM_WEB_IDEMPOTENCY_IN_PROGRESS");
        assertThat(probe.runs.get()).as("the write is not done twice").isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from platform_idempotency.idempotent_responses where response_text is not null").query(Long.class).single()).isZero();
        assertThat(meters.counter("forcitis.platform.web.idempotency.response.oversize").count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: the held key is not taken over after the in-progress timeout (a plain in-progress row is), only once the key expires")
    void heldKeyOutlivesTheInProgressTimeout() throws Exception {
        UserId user = TestAccounts.account(1);
        Duration reclaimAfter = Duration.ofMinutes(2);
        Instant now = Instant.now();
        mvc.perform(sized("k-held", IdempotentResponses.MAX_RESPONSE_LENGTH + 1)).andReturn();
        assertThat(jdbc.sql("select updated_at = expires_at from platform_idempotency.idempotent_responses where idempotency_key = 'k-held'").query(Boolean.class).single())
                .as("the hold pushes updated_at to expires_at").isTrue();
        String hash = jdbc.sql("select request_hash from platform_idempotency.idempotent_responses where idempotency_key = 'k-held'").query(String.class).single();
        Instant expiresAt = jdbc.sql("select expires_at from platform_idempotency.idempotent_responses where idempotency_key = 'k-held'").query(Timestamp.class).single().toInstant();

        assertThat(responses.claim(user, "k-plain", hash, now, now.plus(Duration.ofHours(24)), reclaimAfter)).isTrue();
        assertThat(responses.claim(user, "k-plain", hash, now.plus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(24)), reclaimAfter))
                .as("control: an in-progress row without the hold is taken over after the timeout").isTrue();

        assertThat(responses.claim(user, "k-held", hash, now.plus(Duration.ofMinutes(10)), expiresAt, reclaimAfter))
                .as("the held row is not taken over 10 minutes later").isFalse();
        assertThat(responses.claim(user, "k-held", hash, expiresAt.minusSeconds(1), expiresAt.plus(Duration.ofHours(24)), reclaimAfter))
                .as("nor just before it expires").isFalse();
        assertThat(probe.runs.get()).isEqualTo(1);

        assertThat(responses.claim(user, "k-held", hash, expiresAt.plusSeconds(1), expiresAt.plus(Duration.ofHours(24)), reclaimAfter))
                .as("after expires_at the key can be claimed again").isTrue();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: a POST that is not on the list runs every time and stores nothing, even with a well-formed key")
    void unlistedEndpointStoresNothing() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(UNLISTED).with(user(TestAccounts.account(1).value().toString())).with(csrf()).header(KEY, "k-unlisted")).andReturn();
        }

        assertThat(probe.runs.get()).isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from platform_idempotency.idempotent_responses").query(Long.class).single()).isZero();
    }
}
