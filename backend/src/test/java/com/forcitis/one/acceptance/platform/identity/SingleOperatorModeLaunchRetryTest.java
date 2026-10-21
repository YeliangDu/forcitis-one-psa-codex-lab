package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.platform.identity.SingleOperatorModeEnvironment.Outcome;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The retry of a start whose port was taken in between is itself under test, without a container: the child process is replaced by a stub. */
class SingleOperatorModeLaunchRetryTest {

    static Outcome taken(List<String> arguments) {
        int port = Integer.parseInt(arguments.stream().filter(a -> a.startsWith("--server.port=")).findFirst().orElseThrow().substring("--server.port=".length()));
        return new Outcome(false, 1, List.of("Web server failed to start. Port " + port + " was already in use."), port);
    }

    static Outcome up(List<String> arguments) {
        return new Outcome(true, 0, List.of("Started SingleOperatorModeLauncher"), 0);
    }

    @Test
    @DisplayName("SINGLE-OP 启动重试：端口被占用而未启动时换新端口重试，第二次成功；其余参数不变")
    void aTakenPortIsRetriedWithAnotherPort() throws Exception {
        List<List<String>> calls = new ArrayList<>();

        Outcome outcome = SingleOperatorModeEnvironment.launch(List.of("--spring.profiles.active=dev", "--server.port=1"), arguments -> {
            calls.add(List.copyOf(arguments));
            return calls.size() == 1 ? taken(arguments) : up(arguments);
        });

        assertThat(outcome.started()).isTrue();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1)).contains("--spring.profiles.active=dev").doesNotContain("--server.port=1");
        assertThat(calls.get(1).stream().filter(a -> a.startsWith("--server.port=")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SINGLE-OP 启动重试：最多 5 次，之后原样返回最后一次的失败")
    void retriesAreCapped() throws Exception {
        List<List<String>> calls = new ArrayList<>();

        Outcome outcome = SingleOperatorModeEnvironment.launch(List.of("--server.port=1"), arguments -> {
            calls.add(List.copyOf(arguments));
            return taken(arguments);
        });

        assertThat(calls).hasSize(SingleOperatorModeEnvironment.MAX_PORT_ATTEMPTS);
        assertThat(outcome.started()).isFalse();
        assertThat(outcome.output()).contains("was already in use");
    }

    @Test
    @DisplayName("SINGLE-OP 启动重试：被检查拒绝或其它错误绝不重试，原样返回；端口 0 与无端口参数也不重试")
    void everyOtherFailureIsReturnedAsItIs() throws Exception {
        int[] calls = {0};
        Outcome refused = new Outcome(false, 1, List.of("forcitis.platform.identity.single-operator-mode: ... (ADR-0030)"), 1);

        Outcome outcome = SingleOperatorModeEnvironment.launch(List.of("--server.port=1"), arguments -> {
            calls[0]++;
            return refused;
        });
        assertThat(outcome).isSameAs(refused);
        assertThat(calls[0]).isEqualTo(1);

        calls[0] = 0;
        SingleOperatorModeEnvironment.launch(List.of("--server.port=0"), arguments -> {
            calls[0]++;
            return new Outcome(false, 1, List.of("Port 0 was already in use"), 0);
        });
        SingleOperatorModeEnvironment.launch(List.of("--spring.profiles.active=dev"), arguments -> {
            calls[0]++;
            return new Outcome(false, 1, List.of("Port 5 was already in use"), -1);
        });
        assertThat(calls[0]).isEqualTo(2);

        calls[0] = 0;
        Outcome otherPort = SingleOperatorModeEnvironment.launch(List.of("--server.port=7"), arguments -> {
            calls[0]++;
            return new Outcome(false, 1, List.of("Port 9 was already in use"), 7);
        });
        assertThat(calls[0]).as("the message names a port that is not the one this start asked for").isEqualTo(1);
        assertThat(otherPort.started()).isFalse();
    }
}
