package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageRequestLosslessEncodingTest {
    @Test
    @DisplayName("#67 a key with an unpaired high surrogate cannot be issued")
    void unpairedHighSurrogateKeyIsRejected() {
        String key = "before" + (char) 0xD800 + "after";

        assertThatThrownBy(() -> PageRequest.encode("things", key))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("#67 a key with an unpaired low surrogate cannot be issued")
    void unpairedLowSurrogateKeyIsRejected() {
        String key = "before" + (char) 0xDC00 + "after";

        assertThatThrownBy(() -> PageRequest.encode("things", key))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("#67 a valid supplementary Unicode key reads back without replacement")
    void supplementaryUnicodeKeyRoundTripsLosslessly() {
        String key = "before:" + new String(Character.toChars(0x1F680)) + ":after";

        String cursor = PageRequest.encode("things", key);

        assertThat(PageRequest.of(5, cursor, "things").cursorValue()).isEqualTo(key);
    }
}
