package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestPostgresStartupTest {

    @Test
    @DisplayName("a container is started up to three times, each for at most 90 s, instead of once for 120 s")
    void startIsRepeated() {
        var container = TestPostgres.container();

        assertThat(container.getStartupAttempts()).isEqualTo(TestPostgres.START_ATTEMPTS).isEqualTo(3);
        assertThat(TestPostgres.START_TIMEOUT_SECONDS).isEqualTo(90);
    }

    @Test
    @DisplayName("an exhausted start names the root cause, the attempts, the Docker summary and the last container log lines")
    void failureCarriesTheEvidence() {
        List<String> log = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            log.add("line " + i);
        }
        var cause = new IllegalStateException("start failed", new java.io.EOFException("connection cut"));

        String text = TestPostgres.describeFailure("postgres:18", 3, 90, cause, log, "27 of 82 containers running, 16 CPUs");

        assertThat(text).contains("postgres:18", "3 attempts of 90 s", "java.io.EOFException: connection cut", "27 of 82 containers running, 16 CPUs")
                .contains("last 40 container log lines (of 100)", "line 100", "line 61").doesNotContain("line 60\n").doesNotContain("line 1\n");
    }

    @Test
    @DisplayName("a short log is shown whole and a cause without a chain is its own root")
    void shortLog() {
        String text = TestPostgres.describeFailure("postgres:18", 3, 90, new IllegalStateException("x"), List.of("a", "b"), "info unavailable");

        assertThat(text).contains("java.lang.IllegalStateException: x", "last 2 container log lines ---", "a\nb").doesNotContain("(of ");
    }
}
