package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LegalEntityIdTest {

    @Test
    void mustBePositive() {
        assertThatThrownBy(() -> new LegalEntityId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegalEntityId(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new LegalEntityId(1)).isEqualTo(new LegalEntityId(1));
    }
}
