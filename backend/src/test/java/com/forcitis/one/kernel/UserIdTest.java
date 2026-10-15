package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserIdTest {

    @Test
    @DisplayName("R-1b P2 ADR-0033 3.2 an account id is read only in its lower-case canonical form; the old number and lenient forms are refused")
    void onlyTheCanonicalFormIsRead() {
        UUID id = UUID.fromString("01900000-0000-7000-8000-00000000000a");

        assertThat(UserId.parse("01900000-0000-7000-8000-00000000000a")).isEqualTo(new UserId(id));
        assertThatThrownBy(() -> UserId.parse("70001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserId.parse("1-2-3-4-5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserId.parse("01900000-0000-7000-8000-00000000000A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserId.parse("00000000-0000-0000-0000-000000000000")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new UserId(id)).hasToString(id.toString());
    }
}
