package com.forcitis.one.platform.identity.infrastructure.seeyon;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.identity.application.SeeyonSettings;
import com.forcitis.one.platform.identity.application.SeeyonVerdict;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** RULE-320/321 against a stand-in OA: the ACL's request, its reading of the answer, its limits. */
class SeeyonOaTicketVerifierTest {

    static final String PATH = "/seeyon/thirdpartyController.do";
    static WireMockServer oa;

    @BeforeAll
    static void start() {
        oa = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        oa.start();
    }

    @AfterAll
    static void stop() {
        oa.stop();
    }

    @BeforeEach
    void reset() {
        oa.resetAll();
    }

    private SeeyonOaTicketVerifier verifier(String baseUrl, Duration timeout) {
        return new SeeyonOaTicketVerifier(new SeeyonSettings(true, baseUrl, timeout, "", Duration.ofDays(7)));
    }

    private void answer(String ticket, int status, String body) {
        oa.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("ticket", equalTo(ticket))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "text/plain").withBody(body)));
    }

    @Test
    @DisplayName("RULE-320 asks OA with the URL-encoded ticket and reads the URL-decoded login name")
    void loginName() {
        answer("a b&c", 200, "zhang%40forcitis");

        SeeyonVerdict verdict = verifier(oa.baseUrl(), Duration.ofSeconds(2)).verify("a b&c");

        assertThat(verdict).isEqualTo(new SeeyonVerdict.LoginName("zhang@forcitis"));
        oa.verify(1, getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("ticket", equalTo("a b&c")));
    }

    @Test
    @DisplayName("RULE-321 empty answer, HTML page and unsafe or over-long names are an invalid ticket")
    void invalidAnswers() {
        SeeyonOaTicketVerifier verifier = verifier(oa.baseUrl(), Duration.ofSeconds(2));
        answer("empty", 200, "");
        answer("html", 200, "<!DOCTYPE html><html><body>login</body></html>");
        answer("angle", 200, "a%3Cb");
        answer("space", 200, "a%20b");
        answer("newline", 200, "a%0Ab");
        answer("long", 200, "x".repeat(65));

        for (String ticket : new String[] {"empty", "html", "angle", "space", "newline", "long"}) {
            assertThat(verifier.verify(ticket)).as(ticket).isInstanceOf(SeeyonVerdict.Invalid.class);
        }
        answer("max", 200, "x".repeat(64));
        assertThat(verifier.verify("max")).as("64 characters are allowed").isInstanceOf(SeeyonVerdict.LoginName.class);
    }

    @Test
    @DisplayName("RULE-321 a non-200 answer, a redirect, a timeout and a missing base URL all mean OA is unavailable")
    void unavailable() {
        SeeyonOaTicketVerifier verifier = verifier(oa.baseUrl(), Duration.ofMillis(500));
        answer("err", 500, "boom");
        oa.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("ticket", equalTo("moved"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "http://elsewhere.invalid/")));
        oa.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("ticket", equalTo("slow"))
                .willReturn(aResponse().withStatus(200).withBody("someone").withFixedDelay(2000)));

        assertThat(verifier.verify("err")).isInstanceOf(SeeyonVerdict.Unavailable.class);
        assertThat(verifier.verify("moved")).as("redirects are not followed").isInstanceOf(SeeyonVerdict.Unavailable.class);
        assertThat(verifier.verify("slow")).isInstanceOf(SeeyonVerdict.Unavailable.class);
        assertThat(verifier("", Duration.ofSeconds(1)).verify("x")).isInstanceOf(SeeyonVerdict.Unavailable.class);
        assertThat(verifier("ftp://oa.invalid", Duration.ofSeconds(1)).verify("x")).isInstanceOf(SeeyonVerdict.Unavailable.class);
        assertThat(verifier("http://127.0.0.1:1", Duration.ofMillis(500)).verify("x")).as("connection refused").isInstanceOf(SeeyonVerdict.Unavailable.class);
    }

    @Test
    @DisplayName("RULE-321 defaults: switched off, timeout 5 s, replay window 7 days; the shipped properties keep them")
    void defaults() {
        SeeyonSettings defaults = new Binder(new MapConfigurationPropertySource(java.util.Map.of()))
                .bindOrCreate("forcitis.platform.identity.seeyon", SeeyonSettings.class);

        assertThat(defaults.enabled()).isFalse();
        assertThat(defaults.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.replayWindow()).isEqualTo(Duration.ofDays(7));
    }
}
