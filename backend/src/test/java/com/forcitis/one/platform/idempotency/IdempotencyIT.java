package com.forcitis.one.platform.idempotency;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.IdempotentResponses;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** Idempotency-Key: the same user, key and request get the first answer again for 24 hours; nothing that hands out a credential is stored. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
@Testcontainers
@Import(IdempotencyIT.Probe.class)
class IdempotencyIT {
    static final String ORDERS = "/api/v1/platform/idempotency-probe/orders";
    static final String SESSIONS = "/api/v1/platform/idempotency-probe/sessions:echo";
    static final String KEY = "Idempotency-Key";

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

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = TestPostgres.container()
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

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
                update platform_idempotency.idempotent_responses set response_number = null, response_text = null, updated_at = :old
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
}
