package com.forcitis.one.platform.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.audit.domain.AuditPageMark;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuditCursorTest {
    private static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    private static final String ID = "0192f8a0-0000-7000-8000-000000000001";

    private static PageRequest cursor(String key) {
        return PageRequest.of(null, PageRequest.encode("audit-entries", key), "audit-entries");
    }

    @Test
    @DisplayName("a cursor key the server issued reads back to the same time and entry")
    void roundTrip() {
        AuditPageMark mark = AuditCursor.decode(cursor("1760000000123456|" + ID), NOW);
        assertThat(mark.createdAt()).isEqualTo(Instant.parse("2025-10-09T08:53:20.123456Z"));
        assertThat(mark.entryId().value().toString()).isEqualTo(ID);
        assertThat(AuditCursor.decode(cursor("0|" + ID), NOW).createdAt()).isEqualTo(Instant.EPOCH);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "1760000000123456|" + ID + "|extra",
            "+1760000000123456|" + ID,
            "-1|" + ID,
            "|" + ID,
            "1760000000123456",
            "1760000000123456|",
            "17600000001234567|" + ID,
            "9999999999999999|" + ID,
            "12a|" + ID,
            "1760000000123456|not-a-uuid"})
    @DisplayName("a cursor key with extra parts, a signed or non-digit time, a time past one year ahead or a bad entry id is refused as an invalid cursor")
    void malformedKeysAreRefused(String key) {
        assertThatThrownBy(() -> AuditCursor.decode(cursor(key), NOW)).isInstanceOf(InvalidCursorException.class);
    }
}
