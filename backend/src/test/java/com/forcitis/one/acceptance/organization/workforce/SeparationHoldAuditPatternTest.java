package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #873: the session-mode pattern of the release audit tests reads the mode field only, never the word inside "remainingAdministrators". */
class SeparationHoldAuditPatternTest {

    private static final String ORDINARY_DETAIL = "{\"sessionMode\":\"ORDINARY\",\"remainingAdministrators\":1,\"reason\":\"x\"}";
    private static final String ADMINISTRATOR_DETAIL = "operator: u, session mode: ADMINISTRATOR, remaining super_admin: 1";

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 3 项（行 91）：模式模式只认会话模式字段：ORDINARY 的 detail 不满足 ADMINISTRATOR 断言（即使含 remainingAdministrators），反之亦然")
    void theModePatternBindsToTheModeField() {
        assertThat(ORDINARY_DETAIL).containsPattern(SeparationHoldSupport.sessionModePattern("ORDINARY"));
        assertThat(ORDINARY_DETAIL).doesNotContainPattern(SeparationHoldSupport.sessionModePattern("ADMINISTRATOR"));
        assertThat(ADMINISTRATOR_DETAIL).containsPattern(SeparationHoldSupport.sessionModePattern("ADMINISTRATOR"));
        assertThat(ADMINISTRATOR_DETAIL).doesNotContainPattern(SeparationHoldSupport.sessionModePattern("ORDINARY"));
    }
}
