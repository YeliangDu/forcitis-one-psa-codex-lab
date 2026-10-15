package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class ListPagingKernelTest {
    private static String raw(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("K-3a the sizes of lists, batches, exports and imports are the agreed ones")
    void limitsAreTheAgreedOnes() {
        assertThat(new int[] {ListLimits.DEFAULT_LIMIT, ListLimits.MAX_LIMIT, ListLimits.BATCH_MAX, ListLimits.EXPORT_MAX, ListLimits.IMPORT_MAX,
                ListLimits.SMALL_TABLE_MAX}).containsExactly(50, 200, 100, 10_000, 5_000, 1_000);
    }

    @Test
    @DisplayName("K-3a a cursor round-trips: the key comes back, also when the key itself contains colons")
    void cursorRoundTrips() {
        PageRequest page = PageRequest.of(5, PageRequest.encode("things", "a:b|c"), "things");

        assertThat(page.limit()).isEqualTo(5);
        assertThat(page.cursorValue()).isEqualTo("a:b|c");
    }

    @Test
    @DisplayName("K-3a the longest key of any list (128 Chinese characters) is issued and read back")
    void longestKeyRoundTrips() {
        String key = "号".repeat(128);

        assertThat(PageRequest.of(1, PageRequest.encode("legacy-number-prefix", key), "legacy-number-prefix").cursorValue()).isEqualTo(key);
    }

    @Test
    @DisplayName("K-3a a key that would not read back (control character, empty) is not issued: it fails and raises an ALERT line")
    void unreadableKeyIsNotIssued() {
        Logger logger = (Logger) LoggerFactory.getLogger(PageRequest.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            assertThatThrownBy(() -> PageRequest.encode("things", "ab\u0001cd")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PageRequest.encode("things", "")).isInstanceOf(IllegalStateException.class);
            assertThat(logged.list).hasSize(2).allSatisfy(event -> assertThat(event.getFormattedMessage()).startsWith("ALERT ").contains("things"));
        } finally {
            logger.detachAppender(logged);
        }
    }

    @Test
    @DisplayName("K-3a no limit means 50, no cursor means the first page")
    void defaults() {
        PageRequest page = PageRequest.of(null, null, "things");

        assertThat(page.limit()).isEqualTo(50);
        assertThat(page.cursorValue()).isNull();
        assertThat(PageRequest.of(null, " ", "things").cursorValue()).isNull();
    }

    @ParameterizedTest(name = "limit {0}")
    @ValueSource(ints = {0, -1, 201})
    @DisplayName("K-3a a limit outside 1..200 is refused, not cut")
    void limitOutsideTheRangeIsRefused(int limit) {
        assertThatThrownBy(() -> PageRequest.of(limit, null, "things")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("K-3a a cursor without the version, with another version, another list, an empty key, a control character or padding is refused")
    void cursorsThatAreNotOursAreRefused() {
        String[] refused = {raw("things:key"), raw("v2:things:key"), raw("v1:other:key"), raw("v1:things:"), raw("v1:things"), raw("v1:things:ke\ny"),
                raw("v1:things:key") + "=", "not base64!", raw("v1:things:" + "x".repeat(1100))};

        for (String cursor : refused) {
            assertThatThrownBy(() -> PageRequest.of(1, cursor, "things")).as(cursor).isInstanceOf(InvalidCursorException.class);
        }
    }

    @Test
    @DisplayName("K-3a bytes that are not UTF-8 are refused")
    void cursorWithInvalidUtf8IsRefused() {
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {'v', '1', ':', 't', ':', (byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> PageRequest.of(1, cursor, "t")).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    @DisplayName("K-3a a key read as a UUID or a number that is not one is a bad cursor, not a server error")
    void typedKeysAreChecked() {
        UUID id = UUID.randomUUID();
        assertThat(PageRequest.of(1, PageRequest.encode("t", id.toString()), "t").cursorUuid()).isEqualTo(id);
        assertThat(PageRequest.of(1, PageRequest.encode("t", "2031"), "t").cursorNumber()).isEqualTo(2031);
        assertThat(PageRequest.of(1, null, "t").cursorUuid()).isNull();
        assertThat(PageRequest.of(1, null, "t").cursorNumber()).isNull();

        assertThatThrownBy(() -> PageRequest.of(1, PageRequest.encode("t", "notauuid"), "t").cursorUuid()).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> PageRequest.of(1, PageRequest.encode("t", id.toString().toUpperCase()), "t").cursorUuid()).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> PageRequest.of(1, PageRequest.encode("t", "20x"), "t").cursorNumber()).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> PageRequest.of(1, PageRequest.encode("t", "99999999999"), "t").cursorNumber()).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    @DisplayName("K-3a sort accepts allowed fields in order with - for descending, and refuses the rest by name")
    void sortIsAWhitelist() {
        Set<String> allowed = Set.of("code", "createdAt");

        assertThat(SortOrder.parse(null, allowed).keys()).isEmpty();
        assertThat(SortOrder.parse("", allowed).keys()).isEmpty();
        assertThat(SortOrder.parse("code,-createdAt", allowed).keys())
                .containsExactly(new SortOrder.Key("code", false), new SortOrder.Key("createdAt", true));
        for (String bad : new String[] {"name", "code,", ",code", "--code", "code,code", "code,-code", "CODE"}) {
            assertThatThrownBy(() -> SortOrder.parse(bad, allowed)).as(bad).isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    @DisplayName("K-3a an unpaged list over 1,000 rows fails and raises an ALERT line; at 1,000 it passes")
    void smallTableCapFailsLoudly() {
        Logger logger = (Logger) LoggerFactory.getLogger(SmallTable.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            SmallTable.requireWithinCap("things", 1000);
            assertThat(logged.list).isEmpty();

            assertThatThrownBy(() -> SmallTable.requireWithinCap("things", 1001)).isInstanceOf(IllegalStateException.class);
            assertThat(logged.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("ERROR");
                assertThat(event.getFormattedMessage()).startsWith("ALERT ").contains("things").contains("1001");
            });
        } finally {
            logger.detachAppender(logged);
        }
    }
}
