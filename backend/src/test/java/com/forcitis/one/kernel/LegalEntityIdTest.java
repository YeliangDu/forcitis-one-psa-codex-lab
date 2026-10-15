package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegalEntityIdTest {

    @Test
    void needsAValueThatIsNotTheNilUuid() {
        assertThatThrownBy(() -> new LegalEntityId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LegalEntityId(new UUID(0, 0))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new LegalEntityId(TestIds.uuid(1))).isEqualTo(new LegalEntityId(TestIds.uuid(1)));
    }
}
