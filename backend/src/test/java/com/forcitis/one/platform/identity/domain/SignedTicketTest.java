package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** C1a-03: format, signature and time window of a signed one-time ticket. */
class SignedTicketTest {

    /** Random per run: no key literal in the repository (gitleaks). */
    static final byte[] KEY = randomKey();

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return key;
    }
    static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    static final String NONCE = "0123456789abcdef0123";

    private static String ticket(Instant issuedAt, Instant expiresAt) {
        return SignedTicket.sign(KEY, "km-key-1", "km", 1001, NONCE, issuedAt, expiresAt);
    }

    @Test
    @DisplayName("a signed ticket parses back to its claims and verifies with the issuer's key only")
    void roundTrip() {
        SignedTicket parsed = SignedTicket.parse(ticket(NOW, NOW.plusSeconds(30))).orElseThrow();

        assertThat(parsed.kid()).isEqualTo("km-key-1");
        assertThat(parsed.issuer()).isEqualTo("km");
        assertThat(parsed.subject()).isEqualTo(1001);
        assertThat(parsed.nonce()).isEqualTo(NONCE);
        assertThat(parsed.expiresAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(parsed.isSignedWith(KEY)).isTrue();
        assertThat(parsed.isSignedWith("another-key-another-key-another-key".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    @DisplayName("changing any claim breaks the signature")
    void tamperingIsDetected() {
        String genuine = ticket(NOW, NOW.plusSeconds(30));
        String[] parts = genuine.split("\\.");
        for (String[] change : new String[][] {{"|1001|", "|1002|"}, {"km-key-1|", "km-key-2|"}, {"|km|", "|bi|"}}) {
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).replace(change[0], change[1]);
            String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[1];
            assertThat(SignedTicket.parse(forged).orElseThrow().isSignedWith(KEY)).as(change[1]).isFalse();
        }
    }

    @Test
    @DisplayName("the life is at most 60 seconds; expired and future tickets are refused")
    void timeWindow() {
        assertThat(SignedTicket.parse(ticket(NOW, NOW.plusSeconds(60))).orElseThrow().timeProblem(NOW)).isEmpty();
        assertThat(SignedTicket.parse(ticket(NOW, NOW.plusSeconds(61))).orElseThrow().timeProblem(NOW)).isPresent();
        assertThat(SignedTicket.parse(ticket(NOW, NOW)).orElseThrow().timeProblem(NOW)).as("zero life").isPresent();
        assertThat(SignedTicket.parse(ticket(NOW, NOW.plusSeconds(30))).orElseThrow().timeProblem(NOW.plusSeconds(30))).as("expired").isPresent();
        assertThat(SignedTicket.parse(ticket(NOW, NOW.plusSeconds(30))).orElseThrow().timeProblem(NOW.minusSeconds(4)))
                .as("a little clock skew is tolerated").isEmpty();
        assertThat(SignedTicket.parse(ticket(NOW, NOW.plusSeconds(30))).orElseThrow().timeProblem(NOW.minusSeconds(60))).as("from the future").isPresent();
    }

    @Test
    @DisplayName("anything that is not exactly the ticket format is rejected before any key is looked up")
    void malformed() {
        String valid = ticket(NOW, NOW.plusSeconds(30));
        assertThat(SignedTicket.parse(null)).isEmpty();
        assertThat(SignedTicket.parse("")).isEmpty();
        assertThat(SignedTicket.parse("no-dot")).isEmpty();
        assertThat(SignedTicket.parse(valid + ".extra")).isEmpty();
        assertThat(SignedTicket.parse("%%%." + valid.split("\\.")[1])).isEmpty();
        assertThat(SignedTicket.parse("x".repeat(SignedTicket.MAX_LENGTH + 1))).isEmpty();
        String shortNonce = SignedTicket.sign(KEY, "km-key-1", "km", 1001, "short", NOW, NOW.plusSeconds(30));
        assertThat(SignedTicket.parse(shortNonce)).as("nonce under 16 characters").isEmpty();
        String badSubject = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("km-key-1|km|abc|" + NONCE + "|1|2").getBytes(StandardCharsets.UTF_8)) + ".AAAA";
        assertThat(SignedTicket.parse(badSubject)).as("subject must be a number").isEmpty();
    }
}
