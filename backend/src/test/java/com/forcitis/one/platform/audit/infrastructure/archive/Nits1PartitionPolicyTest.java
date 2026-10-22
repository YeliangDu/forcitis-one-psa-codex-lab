package com.forcitis.one.platform.audit.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Nits1PartitionPolicyTest {
    @Test
    @DisplayName("NITS-1 months-ahead 为 1 与 23 通过，0、24、负数、缺失启动失败")
    void monthsAheadMustLieBetweenOneAndTwentyThree() {
        assertThatCode(() -> new AuditPartitionJob.Policy(1)).doesNotThrowAnyException();
        assertThatCode(() -> new AuditPartitionJob.Policy(23)).doesNotThrowAnyException();
        for (Integer invalid : new Integer[] {0, -1, 24, 100, null}) {
            assertThatThrownBy(() -> new AuditPartitionJob.Policy(invalid)).as("months-ahead " + invalid).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("forcitis.platform.audit.partitions.months-ahead").hasMessageContaining("between 1 and 23");
        }
        assertThat(AuditPartitionJob.MAX_MONTHS_AHEAD).isEqualTo(23);
    }
}
