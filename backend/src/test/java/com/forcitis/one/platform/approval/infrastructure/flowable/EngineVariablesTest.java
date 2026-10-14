package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.approval.domain.ProcessVariables;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EngineVariablesTest {
    @Test
    @DisplayName("a module cannot send a variable that starts with routed_: routing flags are the engine's, and the process archive trusts them")
    void routedVariablesAreReserved() {
        ProcessVariables supplied = new ProcessVariables(Map.of(), Map.of("routed_projectManagerReview", List.of("true")));

        assertThatThrownBy(() -> EngineVariables.toEngine(supplied)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("routed_projectManagerReview");
    }

    @Test
    @DisplayName("other variables still go through, and true / false become booleans")
    void otherVariablesPass() {
        Map<String, Object> engine = EngineVariables.toEngine(new ProcessVariables(Map.of(), Map.of("flagged", List.of("true"), "plain", List.of("x"))));

        assertThat(engine).containsEntry("flagged", Boolean.TRUE).containsEntry("plain", "x");
    }
}
