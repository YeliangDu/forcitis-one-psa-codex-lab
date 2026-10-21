package com.forcitis.one.platform.integration.infrastructure.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ScanOutcome;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** ADR-0070 section 2 item 6 and section 4 item 3: encrypted content is never clean, never infected, and is refused by the file service. */
class ClamdReplyTest {
    static final ClamdReply.Definitions DEFINITIONS = new ClamdReply.Definitions("ClamAV 1.4.1", "27435", Instant.parse("2026-10-21T00:00:00Z"));

    @ParameterizedTest
    @ValueSource(strings = {"Heuristics.Encrypted.Zip", "Heuristics.Encrypted.RAR", "Heuristics.Encrypted.7Zip", "Heuristics.Encrypted.PDF",
            "Heuristics.Encrypted.Doc", "Heuristics.Encrypted.OLE2", "Heuristics.Encrypted.OOXML"})
    void everyEncryptedAlertIsEncrypted(String signature) {
        assertThat(ClamdReply.scan("/s/f", "/s/f: " + signature + " FOUND", DEFINITIONS).outcome()).isEqualTo(ScanOutcome.ENCRYPTED);
    }

    @Test
    void theVersionLineGivesEngineDefinitionsAndBuildTime() {
        ClamdReply.Definitions definitions = ClamdReply.version("ClamAV 1.4.1/27435/Tue Oct  7 08:23:45 2026", ZoneId.of("Asia/Shanghai"));
        assertThat(definitions.engineVersion()).isEqualTo("ClamAV 1.4.1");
        assertThat(definitions.definitionsVersion()).isEqualTo("27435");
        assertThat(definitions.builtAt()).isEqualTo(Instant.parse("2026-10-07T00:23:45Z"));
    }

    @Test
    void anUnreadableDateIsUnavailable() {
        assertThatThrownBy(() -> ClamdReply.version("ClamAV 1.4.1/27435/yesterday", ZoneId.of("UTC")))
                .isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    void aFoundWithoutSignatureIsUnavailable() {
        assertThatThrownBy(() -> ClamdReply.scan("/s/f", "/s/f:  FOUND", DEFINITIONS)).isInstanceOf(ExternalSystemUnavailableException.class);
    }
}
