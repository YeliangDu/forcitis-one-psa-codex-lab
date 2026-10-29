package com.forcitis.one.platform.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.integration.application.DeliverWecomCards;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
class WecomDeliveryIT {

    static final String TOKEN_PATH = "/cgi-bin/gettoken";
    static final String SEND_PATH = "/cgi-bin/message/send";
    static final String UPDATE_PATH = "/cgi-bin/message/update_template_card";

    static final WireMockServer wecom = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        wecom.start();
    }

    @DynamicPropertySource
    static void wecomSettings(DynamicPropertyRegistry registry) {
        registry.add("forcitis.platform.integration.wecom.enabled", () -> "true");
        registry.add("forcitis.platform.integration.wecom.base-url", wecom::baseUrl);
        registry.add("forcitis.platform.integration.wecom.corp-id", () -> "corp-" + UUID.randomUUID());
        registry.add("forcitis.platform.integration.wecom.secret", () -> "secret-" + UUID.randomUUID());
        registry.add("forcitis.platform.integration.wecom.agent-id", () -> "1000002");
        registry.add("forcitis.platform.integration.wecom.recipients.restricted", () -> "true");
        registry.add("forcitis.platform.integration.wecom.recipients.allowed", () -> "alice,carol");
        registry.add("forcitis.platform.integration.wecom.delivery.interval", () -> "PT1H");
        registry.add("forcitis.platform.integration.wecom.delivery.duplicate-task-error-codes", () -> "42014");
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    static final TestDatabase postgres = TestPostgres.database(WecomDeliveryIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    WecomGateway gateway;

    @Autowired
    DeliverWecomCards deliver;

    @Autowired
    OutboundDeliveryRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MeterRegistry meters;

    static final WecomUserId ALICE = new WecomUserId("alice");
    static final WecomUserId BOB = new WecomUserId("bob");
    static final WecomUserId CAROL = new WecomUserId("carol");

    @BeforeEach
    void cleanSlate() {
        wecom.resetAll();
        wecom.stubFor(get(urlPathEqualTo(TOKEN_PATH)).willReturn(okJson("{\"errcode\":0,\"access_token\":\"token-1\",\"expires_in\":7200}")));
        jdbc.sql("delete from platform_integration.outbound_deliveries").update();
    }

    private static WecomCard card(String key, WecomUserId... recipients) {
        return new WecomCard(List.of(recipients), "Approval to do", "Quote 17 waits for you", "https://one.example.test/tasks/" + key, subjectOf(key));
    }

    /** The old tests name a card by a text; its subject is the task that text stands for. */
    private static DeliverySubject subjectOf(String key) {
        return DeliverySubject.of(DeliveryPurpose.TASK_ASSIGNED, UUID.nameUUIDFromBytes(key.replace("#handled", "").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void queue(WecomCard card) {
        tx.executeWithoutResult(status -> gateway.send(card));
    }

    private Map<String, Object> row(String key) {
        Map<String, Object> raw = jdbc.sql("select * from platform_integration.outbound_deliveries where subject_id = :id and kind = :kind")
                .param("id", subjectOf(key).id()).param("kind", key.endsWith("#handled") ? "HANDLED_NOTE" : "CARD").query().singleRow();
        Map<String, Object> row = new java.util.HashMap<>(raw);
        row.replaceAll((column, value) -> value instanceof java.sql.Array array ? elements(array) : value);
        return row;
    }

    private static List<String> elements(java.sql.Array array) {
        try {
            return List.of((String[]) array.getArray());
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sendSucceeds(String responseCodeJson) {
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"" + responseCodeJson + "}")));
    }

    private void makeDue(String key) {
        jdbc.sql("update platform_integration.outbound_deliveries set next_attempt_at = now() - interval '1 second' where subject_id = :id and kind = :kind")
                .param("id", subjectOf(key).id()).param("kind", key.endsWith("#handled") ? "HANDLED_NOTE" : "CARD").update();
    }

    private double gauge(String name) {
        return meters.get(name).gauge().value();
    }

    @Test
    @DisplayName("ARCH 3.7 send queues the card in the caller's transaction; nothing goes out until the delivery job runs")
    void cardIsSentAfterCommitByTheJob() {
        sendSucceeds(",\"response_code\":\"rc-1\"");

        queue(card("approval-task:1", ALICE));

        assertThat(row("approval-task:1")).containsEntry("status", "PENDING").containsEntry("attempt_count", 0);
        wecom.verify(0, postRequestedFor(urlPathEqualTo(SEND_PATH)));

        assertThat(deliver.execute()).isEqualTo(1);

        wecom.verify(1, postRequestedFor(urlPathEqualTo(SEND_PATH)).withRequestBody(containing("\"touser\":\"alice\"")));
        assertThat(row("approval-task:1")).containsEntry("status", "SENT").containsEntry("attempt_count", 1)
                .containsEntry("response_code", "rc-1");
    }

    @Test
    @DisplayName("ARCH 3.7 a rolled-back transaction leaves no card behind")
    void rollbackQueuesNothing() {
        tx.executeWithoutResult(status -> {
            gateway.send(card("approval-task:2", ALICE));
            status.setRollbackOnly();
        });

        assertThat(jdbc.sql("select count(*) from platform_integration.outbound_deliveries").query(Long.class).single()).isZero();
        assertThat(deliver.execute()).isZero();
        wecom.verify(0, postRequestedFor(urlPathEqualTo(SEND_PATH)));
    }

    @Test
    @DisplayName("ARCH 3.7 send outside a transaction is refused")
    void sendNeedsTheCallersTransaction() {
        assertThatThrownBy(() -> gateway.send(card("approval-task:3", ALICE))).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("ARCH 3.7 the deduplication key makes sending idempotent: a repeated card is stored and sent once")
    void sameKeyIsSentOnce() {
        sendSucceeds(",\"response_code\":\"rc-1\"");

        queue(card("approval-task:4", ALICE));
        queue(card("approval-task:4", ALICE));
        deliver.execute();
        deliver.execute();

        assertThat(jdbc.sql("select count(*) from platform_integration.outbound_deliveries where subject_id = :id")
                .param("id", subjectOf("approval-task:4").id()).query(Long.class).single()).isEqualTo(1);
        wecom.verify(1, postRequestedFor(urlPathEqualTo(SEND_PATH)));
    }

    @Test
    @DisplayName("RULE-375 recipients outside the allow list are skipped and recorded; the others still get the card")
    void allowListFiltersRecipients() {
        sendSucceeds("");

        queue(card("approval-task:5", ALICE, BOB, CAROL));
        deliver.execute();

        wecom.verify(1, postRequestedFor(urlPathEqualTo(SEND_PATH)).withRequestBody(containing("\"touser\":\"alice|carol\""))
                .withRequestBody(notContaining("bob")));
        assertThat(row("approval-task:5")).containsEntry("status", "SENT").containsEntry("skipped_recipient_codes", List.of("bob"));
    }

    @Test
    @DisplayName("RULE-375 a card whose recipients are all outside the allow list is skipped without any WeCom call")
    void nobodyOnTheAllowListMeansNoCall() {
        queue(card("approval-task:6", BOB));

        assertThat(deliver.execute()).isZero();

        assertThat(row("approval-task:6")).containsEntry("status", "SKIPPED").containsEntry("skipped_recipient_codes", List.of("bob"));
        wecom.verify(0, postRequestedFor(urlPathEqualTo(SEND_PATH)));
    }

    @Test
    @DisplayName("I-6 / RULE-374 a card without any recipient is not sent and is recorded as failed")
    void noRecipientIsRecordedAsFailed() {
        queue(card("approval-task:7"));

        assertThat(deliver.execute()).isZero();

        assertThat(row("approval-task:7")).containsEntry("status", "FAILED");
        assertThat(row("approval-task:7").get("last_error").toString()).contains("no recipient");
        wecom.verify(0, postRequestedFor(urlPathEqualTo(SEND_PATH)));
    }

    @Test
    @DisplayName("I-6 the request that WeCom receives names exact users only")
    void sentRequestNeverBroadcasts() {
        sendSucceeds("");

        queue(card("approval-task:8", ALICE, CAROL));
        deliver.execute();

        wecom.verify(1, postRequestedFor(urlPathEqualTo(SEND_PATH))
                .withRequestBody(notContaining("toparty")).withRequestBody(notContaining("totag")).withRequestBody(notContaining("@all")));
    }

    @Test
    @DisplayName("ARCH 3.7 a failed send is retried after the back-off and succeeds when WeCom recovers")
    void failedSendIsRetried() {
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(500)));
        queue(card("approval-task:9", ALICE));

        deliver.execute();

        assertThat(row("approval-task:9")).containsEntry("status", "PENDING").containsEntry("attempt_count", 1);
        Double secondsUntilRetry = jdbc.sql("select extract(epoch from (next_attempt_at - now())) from platform_integration.outbound_deliveries")
                .query(Double.class).single();
        assertThat(secondsUntilRetry).isBetween(20.0, 31.0);
        assertThat(deliver.execute()).as("not due before the back-off has passed").isZero();

        sendSucceeds(",\"response_code\":\"rc-9\"");
        makeDue("approval-task:9");
        assertThat(deliver.execute()).isEqualTo(1);

        assertThat(row("approval-task:9")).containsEntry("status", "SENT").containsEntry("attempt_count", 2);
    }

    @Test
    @DisplayName("ARCH 3.7/3.9 after 8 failed attempts the card is a dead letter and the dead-letter metric rises")
    void exhaustedRetriesEndInTheDeadLetters() {
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":60020,\"errmsg\":\"not allow to access from your ip\"}")));
        double deadBefore = gauge("forcitis.platform.integration.deliveries.dead.letters");
        queue(card("approval-task:10", ALICE));

        for (int attempt = 1; attempt <= 8; attempt++) {
            makeDue("approval-task:10");
            assertThat(deliver.execute()).isEqualTo(1);
            assertThat(row("approval-task:10")).containsEntry("attempt_count", attempt)
                    .containsEntry("status", attempt < 8 ? "PENDING" : "DEAD_LETTER");
        }

        assertThat(row("approval-task:10").get("last_error").toString()).contains("60020");
        assertThat(gauge("forcitis.platform.integration.deliveries.dead.letters")).isEqualTo(deadBefore + 1);
        makeDue("approval-task:10");
        assertThat(deliver.execute()).as("a dead letter is never picked up again").isZero();
        wecom.verify(8, postRequestedFor(urlPathEqualTo(SEND_PATH)));
    }

    @Test
    @DisplayName("ARCH 3.9 a card still pending 10 minutes after it was queued shows in the overdue metric until it is sent")
    void overdueMetricCountsStalePendingCards() {
        sendSucceeds("");
        double before = gauge("forcitis.platform.integration.deliveries.pending.overdue");
        queue(card("approval-task:11", ALICE));
        assertThat(gauge("forcitis.platform.integration.deliveries.pending.overdue")).isEqualTo(before);

        jdbc.sql("update platform_integration.outbound_deliveries set created_at = now() - interval '11 minutes'").update();
        assertThat(gauge("forcitis.platform.integration.deliveries.pending.overdue")).isEqualTo(before + 1);

        deliver.execute();
        assertThat(gauge("forcitis.platform.integration.deliveries.pending.overdue")).isEqualTo(before);
    }

    private void markHandled(String key, String note) {
        tx.executeWithoutResult(status -> gateway.markHandled(subjectOf(key), note));
    }

    @Test
    @DisplayName("Q-3 markHandled after the card was sent queues a replacement that the job delivers with the response code, once")
    void markHandledReplacesTheCard() {
        sendSucceeds(",\"response_code\":\"rc-12\"");
        wecom.stubFor(post(urlPathEqualTo(UPDATE_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"}")));
        queue(card("approval-task:12", ALICE, CAROL));
        deliver.execute();

        markHandled("approval-task:12", "handled by Dave");
        markHandled("approval-task:12", "handled by Dave");
        wecom.verify(0, postRequestedFor(urlPathEqualTo(UPDATE_PATH)));
        assertThat(deliver.execute()).isEqualTo(1);
        assertThat(deliver.execute()).isZero();

        wecom.verify(1, postRequestedFor(urlPathEqualTo(UPDATE_PATH)).withRequestBody(containing("\"response_code\":\"rc-12\""))
                .withRequestBody(containing("handled by Dave")).withRequestBody(containing("\"userids\":[\"alice\",\"carol\"]")));
        assertThat(row("approval-task:12")).containsEntry("handled_result", "UPDATED").containsEntry("handled_note", "handled by Dave");
        assertThat(row("approval-task:12#handled")).containsEntry("status", "SENT").containsEntry("kind", "HANDLED_NOTE");
    }

    @Test
    @DisplayName("Q-3 markHandled while the card is being sent: the card is delivered, then replaced right away")
    void handledWhileTheCardIsInFlightIsStillReplaced() throws Exception {
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\",\"response_code\":\"rc-16\"}")
                .withFixedDelay(1500)));
        wecom.stubFor(post(urlPathEqualTo(UPDATE_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"}")));
        queue(card("approval-task:16", ALICE));

        var sending = java.util.concurrent.CompletableFuture.runAsync(deliver::execute);
        await("the card send reached WeCom").atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20))
                .until(() -> wecom.countRequestsMatching(postRequestedFor(urlPathEqualTo(SEND_PATH)).build()).getCount() > 0);
        markHandled("approval-task:16", "handled by Dave");
        sending.get(10, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(row("approval-task:16")).containsEntry("status", "SENT");
        assertThat(row("approval-task:16#handled")).containsEntry("status", "PENDING");
        assertThat(deliver.execute()).isEqualTo(1);
        wecom.verify(1, postRequestedFor(urlPathEqualTo(UPDATE_PATH)).withRequestBody(containing("\"response_code\":\"rc-16\"")));
        assertThat(row("approval-task:16")).containsEntry("handled_result", "UPDATED");
    }

    @Test
    @DisplayName("Q-3 a failing replacement is retried like a card; an unknown key is ignored")
    void failingReplacementIsRetried() {
        sendSucceeds(",\"response_code\":\"rc-15\"");
        wecom.stubFor(post(urlPathEqualTo(UPDATE_PATH)).willReturn(okJson("{\"errcode\":40037,\"errmsg\":\"invalid response code\"}")));
        queue(card("approval-task:15", ALICE));
        deliver.execute();

        markHandled("approval-task:15", "handled by Dave");
        markHandled("approval-task:unknown", "handled by Dave");

        deliver.execute();
        assertThat(row("approval-task:15#handled")).containsEntry("status", "PENDING").containsEntry("attempt_count", 1);

        wecom.stubFor(post(urlPathEqualTo(UPDATE_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"}")));
        makeDue("approval-task:15#handled");
        deliver.execute();
        assertThat(row("approval-task:15#handled")).containsEntry("status", "SENT").containsEntry("attempt_count", 2);
        assertThat(row("approval-task:15")).containsEntry("handled_result", "UPDATED");
    }

    @Test
    @DisplayName("Q-3 a replacement that fails every attempt is a dead letter and the card records the failure")
    void exhaustedReplacementIsADeadLetter() {
        sendSucceeds(",\"response_code\":\"rc-17\"");
        wecom.stubFor(post(urlPathEqualTo(UPDATE_PATH)).willReturn(okJson("{\"errcode\":40037,\"errmsg\":\"invalid response code\"}")));
        queue(card("approval-task:17", ALICE));
        deliver.execute();
        markHandled("approval-task:17", "handled by Dave");

        for (int attempt = 1; attempt <= 8; attempt++) {
            makeDue("approval-task:17#handled");
            deliver.execute();
        }

        assertThat(row("approval-task:17#handled")).containsEntry("status", "DEAD_LETTER");
        assertThat(row("approval-task:17").get("handled_result").toString()).startsWith("FAILED").contains("40037");
    }

    @Test
    @DisplayName("Q-3 without a response code the card is left as it is and the reason is recorded")
    void markHandledWithoutResponseCodeDegrades() {
        sendSucceeds("");
        queue(card("approval-task:13", ALICE));
        deliver.execute();

        markHandled("approval-task:13", "handled by Dave");

        assertThat(deliver.execute()).isZero();
        wecom.verify(0, postRequestedFor(urlPathEqualTo(UPDATE_PATH)));
        assertThat(row("approval-task:13").get("handled_result").toString()).startsWith("NOT_APPLIED").contains("no response code");
    }

    @Test
    @DisplayName("Q-3 a card handled before it was delivered is not delivered at all")
    void handledBeforeDeliveryIsNeverSent() {
        queue(card("approval-task:14", ALICE));

        markHandled("approval-task:14", "handled by Dave");
        deliver.execute();

        assertThat(row("approval-task:14")).containsEntry("status", "SKIPPED");
        assertThat(row("approval-task:14").get("handled_result").toString()).startsWith("NOT_APPLIED");
        wecom.verify(0, postRequestedFor(urlPathEqualTo(SEND_PATH)));
    }

    @Test
    @DisplayName("markHandled outside a transaction is refused")
    void markHandledNeedsTheCallersTransaction() {
        assertThatThrownBy(() -> gateway.markHandled(subjectOf("approval-task:x"), "n")).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("ARCH 3.7 a claim whose lease ran out is claimed again; the first holder's late result is rejected")
    void expiredLeaseIsReclaimedAndTheStaleWriteIsRejected() {
        queue(card("approval-task:18", ALICE));
        java.time.Instant now = java.time.Instant.now();
        var first = repository.claimDue(now, java.time.Duration.ofMinutes(2), 20).getFirst();
        assertThat(repository.claimDue(now, java.time.Duration.ofMinutes(2), 20)).as("leased, not due").isEmpty();

        var second = repository.claimDue(now.plus(java.time.Duration.ofMinutes(3)), java.time.Duration.ofMinutes(2), 20).getFirst();

        assertThat(first.attemptCount()).isEqualTo(1);
        assertThat(second.attemptCount()).isEqualTo(2);
        first.markSent("stale-code", now);
        assertThat(repository.save(first)).isFalse();
        var retried = repository.findCard(subjectOf("approval-task:18")).orElseThrow();
        assertThat(retried.attemptCount()).isEqualTo(2);
        var staleRetry = claimedAs(first);
        staleRetry.scheduleRetry(now, "stale");
        assertThat(repository.save(staleRetry)).isFalse();
        var staleDead = claimedAs(first);
        staleDead.deadLetter("stale");
        assertThat(repository.save(staleDead)).isFalse();
        var staleSkip = claimedAs(first);
        staleSkip.skip("stale");
        assertThat(repository.save(staleSkip)).isFalse();
        assertThat(row("approval-task:18")).containsEntry("status", "PENDING").containsEntry("attempt_count", 2);
        second.markSent("rc-18", now);
        assertThat(repository.save(second)).isTrue();
        var again = repository.findCard(subjectOf("approval-task:18")).orElseThrow();
        assertThatThrownBy(() -> again.markSent("again", now)).as("already sent").isInstanceOf(DomainException.class);
        assertThat(row("approval-task:18")).containsEntry("status", "SENT").containsEntry("response_code", "rc-18");
    }

    private static com.forcitis.one.platform.integration.domain.OutboundDelivery claimedAs(
            com.forcitis.one.platform.integration.domain.OutboundDelivery claim) {
        return com.forcitis.one.platform.integration.domain.OutboundDelivery.restore(claim.deliveryId(), claim.subject(),
                claim.recipients(), claim.title(), claim.description(), claim.targetUrl(), "PENDING", claim.attemptCount(), null, null, null,
                claim.kind(), claim.target() == null ? null : claim.target().value(), claim.correlation(), claim.version(), claim.eventSequence());
    }

    @Test
    @DisplayName("ARCH 3.7 the claim lease covers a whole batch at the worst case per delivery")
    void leaseCoversTheWholeBatch() {
        assertThat(DeliverWecomCards.LEASE)
                .isGreaterThanOrEqualTo(DeliverWecomCards.WORST_CASE_PER_DELIVERY.multipliedBy(DeliverWecomCards.BATCH_SIZE));
    }

    @Test
    @DisplayName("ARCH 3.7 'task_id already used' on a repeated send means the card is already out: sent, not retried")
    void repeatedSendWithUsedTaskIdIsSent() {
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":42014,\"errmsg\":\"task_id used\"}")));
        queue(card("approval-task:19", ALICE));

        deliver.execute();

        assertThat(row("approval-task:19")).containsEntry("status", "SENT").containsEntry("attempt_count", 1);
        assertThat(row("approval-task:19").get("response_code")).isNull();
    }

    @Test
    @DisplayName("RULE-322/323 the gateway exchanges a code for the WeCom user id; any failure answers empty and a blank code never calls WeCom")
    void codeExchangeThroughTheGateway() {
        wecom.stubFor(get(urlPathEqualTo("/cgi-bin/auth/getuserinfo")).willReturn(okJson("{\"errcode\":0,\"userid\":\"zhangsan\"}")));
        assertThat(gateway.resolveWecomUserId("code-1", WecomEntry.WORKBENCH)).contains(new WecomUserId("zhangsan"));

        wecom.stubFor(get(urlPathEqualTo("/cgi-bin/auth/getuserinfo")).willReturn(aResponse().withStatus(500)));
        assertThat(gateway.resolveWecomUserId("code-2", WecomEntry.QR_CODE_LOGIN)).isEmpty();

        wecom.resetRequests();
        assertThat(gateway.resolveWecomUserId(" ", WecomEntry.WORKBENCH)).isEmpty();
        assertThat(wecom.getAllServeEvents()).isEmpty();
    }
}
