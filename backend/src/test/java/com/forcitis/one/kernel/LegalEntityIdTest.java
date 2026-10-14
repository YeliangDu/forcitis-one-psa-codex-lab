package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import org.junit.jupiter.api.Test;

class LegalEntityIdTest {

    @Test
    void needsAValue() {
        assertThatThrownBy(() -> new LegalEntityId(null)).isInstanceOf(NullPointerException.class);
        assertThat(new LegalEntityId(TestIds.uuid(1))).isEqualTo(new LegalEntityId(TestIds.uuid(1)));
    }
}
