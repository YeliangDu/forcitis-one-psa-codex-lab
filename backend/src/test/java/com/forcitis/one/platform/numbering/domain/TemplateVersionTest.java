package com.forcitis.one.platform.numbering.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.numbering.api.ResetPolicy;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateVersionTest {

    @Test
    @DisplayName("NV-05b TemplateVersion 的 id 为空时构造失败")
    void refusesANullId() {
        NumberingPattern pattern = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);
        assertThatThrownBy(() -> new TemplateVersion(null, 1, pattern, 1, LocalDate.parse("2026-10-15"), null, Instant.parse("2026-10-14T08:00:00Z"),
                VersionStatus.PENDING_CONFIRMATION, null, null, null, null)).isInstanceOf(NullPointerException.class);
    }
}
