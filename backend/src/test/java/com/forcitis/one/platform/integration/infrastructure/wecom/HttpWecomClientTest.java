package com.forcitis.one.platform.integration.infrastructure.wecom;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.domain.DeliveryKind;
import com.forcitis.one.platform.integration.domain.DeliveryStatus;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryId;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpWecomClientTest {

    static final String TOKEN_PATH = "/cgi-bin/gettoken";
    static final String SEND_PATH = "/cgi-bin/message/send";
    static final String UPDATE_PATH = "/cgi-bin/message/update_template_card";

    /** The read timeout of every case that expects an answer: a ceiling against a stub that stalls on a loaded machine, not a measured wait; the one case that wants a timeout uses its own. */
    static final Duration GENEROUS = Duration.ofSeconds(30);

    static WireMockServer wecom;
    static final String CORP_ID = "corp-" + UUID.randomUUID();
    static final String SECRET = "secret-" + UUID.randomUUID();

    final TestClock clock = new TestClock();
    HttpWecomClient client;

    @BeforeAll
    static void startWireMock() {
        wecom = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wecom.start();
    }

    @AfterAll
    static void stopWireMock() {
        wecom.stop();
    }

    @BeforeEach
    void freshClient() {
        wecom.resetAll();
        client = clientWith(GENEROUS);
    }

    private List<Integer> duplicateTaskCodes = List.of();

    private HttpWecomClient clientWith(Duration readTimeout) {
        WecomProperties properties = new WecomProperties(true, wecom.baseUrl(), CORP_ID, SECRET, "1000002", GENEROUS,
                readTimeout, new WecomProperties.Recipients(true, List.of()), new WecomProperties.Delivery(Duration.ofSeconds(5), duplicateTaskCodes));
        return new HttpWecomClient(properties, JsonMapper.builder().build(), clock);
    }

    private static void tokenIs(String token, long expiresIn) {
        wecom.stubFor(get(urlPathEqualTo(TOKEN_PATH)).withQueryParam("corpid", equalTo(CORP_ID)).withQueryParam("corpsecret", equalTo(SECRET))
                .willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + "}")));
    }

    private static void sendSucceeds() {
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\",\"response_code\":\"rc-1\"}")));
    }

    private static final com.forcitis.one.platform.integration.api.DeliverySubject SUBJECT = com.forcitis.one.platform.integration.api.DeliverySubject.of(
            com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_ASSIGNED, TestIds.uuid(42));

    private static OutboundDelivery delivery() {
        return OutboundDelivery.restore(new OutboundDeliveryId(TestIds.uuid(1)), SUBJECT, List.of(new WecomUserId("alice"), new WecomUserId("bob")),
                "title", "description", "https://one.example.test/tasks/42", "PENDING", 1, null, null, null, DeliveryKind.CARD, null, null, 0, 0);
    }

    @Test
    @DisplayName("ARCH 3.7 the access token is requested once and reused for later calls")
    void tokenIsCached() {
        tokenIs("token-1", 7200);
        sendSucceeds();

        client.sendCard(delivery());
        client.sendCard(delivery());
        client.sendCard(delivery());

        wecom.verify(1, getRequestedFor(urlPathEqualTo(TOKEN_PATH)));
        wecom.verify(3, postRequestedFor(urlPathEqualTo(SEND_PATH)).withQueryParam("access_token", equalTo("token-1")));
    }

    @Test
    @DisplayName("ARCH 3.7 the access token is renewed 5 minutes before it expires, not after")
    void tokenIsRenewedFiveMinutesBeforeExpiry() {
        tokenIs("token-1", 7200);
        sendSucceeds();
        client.sendCard(delivery());

        clock.advance(Duration.ofSeconds(7200 - 301));
        client.sendCard(delivery());
        wecom.verify(1, getRequestedFor(urlPathEqualTo(TOKEN_PATH)));

        tokenIs("token-2", 7200);
        clock.advance(Duration.ofSeconds(2));
        client.sendCard(delivery());
        wecom.verify(2, getRequestedFor(urlPathEqualTo(TOKEN_PATH)));
        wecom.verify(1, postRequestedFor(urlPathEqualTo(SEND_PATH)).withQueryParam("access_token", equalTo("token-2")));
    }

    @Test
    @DisplayName("a token WeCom reports as invalid is dropped and the call is repeated once with a new one")
    void rejectedTokenIsReplacedOnce() {
        tokenIs("token-old", 7200);
        sendSucceeds();
        client.sendCard(delivery());

        tokenIs("token-new", 7200);
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).withQueryParam("access_token", equalTo("token-old"))
                .willReturn(okJson("{\"errcode\":40014,\"errmsg\":\"invalid access_token\"}")));
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).withQueryParam("access_token", equalTo("token-new"))
                .willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\",\"response_code\":\"rc-2\"}")));

        assertThat(client.sendCard(delivery())).contains("rc-2");
        wecom.verify(2, getRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    }

    @Test
    @DisplayName("a token that is rejected again after renewal is a failure, not an endless loop")
    void secondRejectionFails() {
        tokenIs("token-1", 7200);
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}")));

        assertThatThrownBy(() -> client.sendCard(delivery())).isInstanceOf(ExternalSystemUnavailableException.class);
        wecom.verify(2, getRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    }

    @Test
    @DisplayName("ARCH 3.7 sending posts a text-notice card to touser with the response code returned to the caller")
    void sendReturnsTheResponseCode() {
        tokenIs("token-1", 7200);
        sendSucceeds();

        Optional<String> responseCode = client.sendCard(delivery());

        assertThat(responseCode).contains("rc-1");
        wecom.verify(postRequestedFor(urlPathEqualTo(SEND_PATH))
                .withRequestBody(containing("\"touser\":\"alice|bob\""))
                .withRequestBody(containing("\"agentid\":1000002"))
                .withRequestBody(containing("\"card_type\":\"text_notice\""))
                .withRequestBody(containing("https://one.example.test/tasks/42")));
    }

    @Test
    @DisplayName("a send answered without a response code still succeeds; the caller learns there is none")
    void missingResponseCodeIsEmpty() {
        tokenIs("token-1", 7200);
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"}")));

        assertThat(client.sendCard(delivery())).isEmpty();
    }

    @Test
    @DisplayName("a WeCom error, an HTTP error and an unreadable answer are all failures of the call")
    void failuresAreUnavailable() {
        tokenIs("token-1", 7200);

        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":60020,\"errmsg\":\"not allow to access from your ip\"}")));
        assertThatThrownBy(() -> client.sendCard(delivery())).isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("60020");

        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(502)));
        assertThatThrownBy(() -> client.sendCard(delivery())).isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("502");

        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(200).withBody("<html>")));
        assertThatThrownBy(() -> client.sendCard(delivery())).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    @DisplayName("the read timeout applies: a slow WeCom is a failure of the call")
    void slowAnswerTimesOut() {
        tokenIs("token-1", 7200);
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":0}").withFixedDelay(1500)));
        HttpWecomClient impatient = clientWith(Duration.ofMillis(300));

        assertThatThrownBy(() -> impatient.sendCard(delivery())).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    @DisplayName("RULE-322 the workbench code and the QR-code login code are exchanged at auth/getuserinfo")
    void workbenchAndQrCodeUseGetUserInfo() {
        tokenIs("token-1", 7200);
        wecom.stubFor(get(urlPathEqualTo("/cgi-bin/auth/getuserinfo")).withQueryParam("code", equalTo("code-1"))
                .withQueryParam("access_token", equalTo("token-1"))
                .willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\",\"userid\":\"zhangsan\"}")));

        assertThat(client.userIdOfCode("code-1", WecomEntry.WORKBENCH)).contains(new WecomUserId("zhangsan"));
        assertThat(client.userIdOfCode("code-1", WecomEntry.QR_CODE_LOGIN)).contains(new WecomUserId("zhangsan"));
        wecom.verify(2, getRequestedFor(urlPathEqualTo("/cgi-bin/auth/getuserinfo")));
    }

    @Test
    @DisplayName("RULE-323 the mini-program code is exchanged at miniprogram/jscode2session")
    void miniProgramUsesJsCodeToSession() {
        tokenIs("token-1", 7200);
        wecom.stubFor(get(urlPathEqualTo("/cgi-bin/miniprogram/jscode2session")).withQueryParam("js_code", equalTo("code-2"))
                .withQueryParam("grant_type", equalTo("authorization_code"))
                .willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\",\"corpid\":\"c\",\"userid\":\"lisi\",\"session_key\":\"k\"}")));

        assertThat(client.userIdOfCode("code-2", WecomEntry.MINI_PROGRAM)).contains(new WecomUserId("lisi"));
    }

    @Test
    @DisplayName("RULE-322 an external contact (no member userid) is refused")
    void externalContactIsRefused() {
        tokenIs("token-1", 7200);
        wecom.stubFor(get(urlPathEqualTo("/cgi-bin/auth/getuserinfo"))
                .willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\",\"openid\":\"o-1\",\"external_userid\":\"e-1\"}")));

        assertThat(client.userIdOfCode("code-3", WecomEntry.WORKBENCH)).isEmpty();
    }

    @Test
    @DisplayName("RULE-322/323 errcode 40029 (code expired or invalid) answers empty; any other error is a failure")
    void invalidCodeIsEmptyAndOtherErrorsFail() {
        tokenIs("token-1", 7200);
        wecom.stubFor(get(urlPathEqualTo("/cgi-bin/auth/getuserinfo")).withQueryParam("code", equalTo("expired"))
                .willReturn(okJson("{\"errcode\":40029,\"errmsg\":\"invalid code\"}")));
        wecom.stubFor(get(urlPathEqualTo("/cgi-bin/auth/getuserinfo")).withQueryParam("code", equalTo("blocked"))
                .willReturn(okJson("{\"errcode\":60020,\"errmsg\":\"not allow to access from your ip\"}")));

        assertThat(client.userIdOfCode("expired", WecomEntry.WORKBENCH)).isEmpty();
        assertThatThrownBy(() -> client.userIdOfCode("blocked", WecomEntry.WORKBENCH)).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    @DisplayName("ARCH 3.7 an errcode configured as 'task_id already used' means the card was sent before: sent, without a response code")
    void configuredDuplicateTaskErrorCountsAsSent() {
        tokenIs("token-1", 7200);
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":42014,\"errmsg\":\"task_id used\"}")));

        assertThatThrownBy(() -> client.sendCard(delivery())).isInstanceOf(ExternalSystemUnavailableException.class);

        duplicateTaskCodes = List.of(42014);
        assertThat(clientWith(GENEROUS).sendCard(delivery())).isEmpty();
    }

    @Test
    @DisplayName("Q-3 replacing a card posts the response code and the exact recipients with the handled note")
    void replacementUsesTheResponseCode() {
        tokenIs("token-1", 7200);
        wecom.stubFor(post(urlPathEqualTo(UPDATE_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"}")));
        OutboundDelivery sent = OutboundDelivery.restore(new OutboundDeliveryId(TestIds.uuid(1)), SUBJECT,
                List.of(new WecomUserId("alice"), new WecomUserId("bob")), "title", "description", "https://one.example.test/tasks/42",
                "SENT", 1, "rc-1", null, null, DeliveryKind.CARD, null, null, 0, 0);

        OutboundDelivery note = OutboundDelivery.restore(new OutboundDeliveryId(TestIds.uuid(2)), SUBJECT, List.of(new WecomUserId("alice"), new WecomUserId("bob")),
                "title", "handled by Carol", "https://one.example.test/tasks/42", "PENDING", 1, null, null, null, DeliveryKind.HANDLED_NOTE, TestIds.uuid(1), null, 0, 0);

        client.replaceWithHandledNote(sent, note);

        StringValuePattern body = containing("\"response_code\":\"rc-1\"");
        wecom.verify(postRequestedFor(urlPathEqualTo(UPDATE_PATH)).withRequestBody(body)
                .withRequestBody(containing("\"userids\":[\"alice\",\"bob\"]")).withRequestBody(containing("handled by Carol")));
    }

    @Test
    @DisplayName("neither the secret nor the token shows up in a failure message")
    void failureMessagesHoldNoCredentials() {
        wecom.stubFor(get(urlPathEqualTo(TOKEN_PATH)).willReturn(okJson("{\"errcode\":40013,\"errmsg\":\"invalid corpid\"}")));

        assertThatThrownBy(() -> client.sendCard(delivery())).isInstanceOf(ExternalSystemUnavailableException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET).doesNotContain(CORP_ID));
    }

    static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-10-14T08:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
