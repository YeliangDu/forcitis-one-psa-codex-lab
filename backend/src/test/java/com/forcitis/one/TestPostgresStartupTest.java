package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestPostgresStartupTest {

    @Test
    @DisplayName("a container is started up to three times, each waiting at most 90 s for its first JDBC connection instead of 120 s")
    void startIsRepeated() {
        var container = (TestPostgres.Diagnosed) TestPostgres.container();

        assertThat(container.getStartupAttempts()).isEqualTo(3);
        assertThat(container.connectSeconds()).isEqualTo(90);
    }

    @Test
    @DisplayName("a start that fails is reported with the cause, the attempts and the connection wait as set on the container, the log lines it wrote and the engine summary")
    void failedStartIsExplained() {
        var container = new TestPostgres.Diagnosed("postgres:18");
        container.startWith(() -> {
            container.remember("boom 1\nboom 2\n");
            throw new IllegalStateException("could not start", new java.io.EOFException("cut"));
        });
        container.withStartupAttempts(2).withConnectTimeoutSeconds(45);

        assertThatThrownBy(container::start).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IllegalStateException.class)
                .message().contains("postgres:18", "2 attempts (connection wait 45 s each)", "java.io.EOFException: cut", "--- docker: ", "boom 1\nboom 2");
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

        assertThat(text).contains("postgres:18", "3 attempts (connection wait 90 s each)", "java.io.EOFException: connection cut", "27 of 82 containers running, 16 CPUs")
                .contains("last 40 container log lines (of 100)", "line 100", "line 61").doesNotContain("line 60\n").doesNotContain("line 1\n");
    }

    @Test
    @DisplayName("a short log is shown whole and a cause without a chain is its own root")
    void shortLog() {
        String text = TestPostgres.describeFailure("postgres:18", 3, 90, new IllegalStateException("x"), List.of("a", "b"), "info unavailable");

        assertThat(text).contains("java.lang.IllegalStateException: x", "last 2 container log lines ---", "a\nb").doesNotContain("(of ");
    }
}
