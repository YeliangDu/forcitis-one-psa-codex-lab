package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A child JVM of the real main that is left to end on its own, as an operations command does; start-up output and the exit code are the real ones. */
final class SingleOperatorModeProcess {

    record Ended(int exitCode, List<String> lines) {
        String output() {
            return String.join("\n", lines);
        }
    }

    private SingleOperatorModeProcess() {
    }

    static Ended runToEnd(List<String> arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.timezone=UTC", "-cp",
                System.getProperty("java.class.path"), SingleOperatorModeLauncher.class.getName()));
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).directory(new java.io.File(System.getProperty("java.io.tmpdir"))).redirectErrorStream(true).start();
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException ended) {
                lines.add("reader ended: " + ended.getMessage());
            }
        });
        boolean ended = process.waitFor(240, TimeUnit.SECONDS);
        if (!ended) {
            process.destroyForcibly();
        }
        reader.join(10_000);
        assertThat(ended).as("the process ends on its own; output so far:\n" + String.join("\n", lines)).isTrue();
        return new Ended(process.exitValue(), List.copyOf(lines));
    }
}
