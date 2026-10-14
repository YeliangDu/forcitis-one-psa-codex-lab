package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** C1a-04: TOTP per RFC 6238 (HMAC-SHA1, 30 s, 6 digits = the last six digits of the RFC's 8-digit test values). */
class TotpTest {

    static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("RFC 6238 Appendix B test vectors")
    void rfcVectors() {
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1111111109L)))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1111111111L)))).isEqualTo("050471");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1234567890L)))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(2000000000L)))).isEqualTo("279037");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(20000000000L)))).isEqualTo("353130");
    }

    @Test
    @DisplayName("a code is accepted in its own step and one step either side, and reports which step it belongs to")
    void windowAndStep() {
        Instant now = Instant.ofEpochSecond(1111111109L);
        long step = Totp.stepAt(now);

        assertThat(Totp.verify(RFC_SECRET, "081804", now, 1)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 1)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, 1)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, 1)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, 1)).isEmpty();
    }

    @Test
    @DisplayName("malformed codes never match")
    void malformedCodes() {
        Instant now = Instant.ofEpochSecond(1111111109L);

        assertThat(Totp.verify(RFC_SECRET, null, now, 1)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "", now, 1)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "08180", now, 1)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "0818041", now, 1)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "08180x", now, 1)).isEmpty();
    }

    @Test
    @DisplayName("Base32 round trip for the authenticator-app secret")
    void base32() {
        assertThat(Totp.toBase32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.fromBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).isEqualTo(RFC_SECRET);
        byte[] random = Totp.newSecret();
        assertThat(Totp.fromBase32(Totp.toBase32(random))).isEqualTo(random);
    }
}
