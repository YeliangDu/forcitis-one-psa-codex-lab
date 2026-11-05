package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every state machine declared in main is exhausted by a TransitionTable test over its status enum. */
class StateMachineCoverageGuardTest {

    private static final Pattern DECLARED = Pattern.compile("StateMachine\\.of\\(\\s*(\\w+)\\.class\\s*\\)");
    private static final Pattern EXHAUSTED = Pattern.compile("new\\s+TransitionTable<(?:\\s*(\\w+)\\s*,|>\\(\\s*(\\w+)\\.class)");

    @Test
    @DisplayName("each StateMachine.of(X.class) in main has a new TransitionTable<X, ...> or new TransitionTable<>(X.class, ...) in test")
    void everyMachineIsExhausted() throws IOException {
        Set<String> declared = found(Path.of("src/main/java"), DECLARED);
        Set<String> exhausted = found(Path.of("src/test/java"), EXHAUSTED);

        assertThat(declared).as("state machines declared in main (the scan itself works)").hasSizeGreaterThanOrEqualTo(17);
        declared.removeAll(exhausted);
        assertThat(declared).as("state machines with no TransitionTable test: add one that tries every operation from every state").isEmpty();
    }

    private static Set<String> found(Path root, Pattern pattern) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).filter(f -> !f.getFileName().toString().equals("TransitionTable.java"))
                    .filter(f -> !f.getFileName().toString().equals("StateMachineCoverageGuardTest.java")).toList()) {
                Matcher m = pattern.matcher(Files.readString(file));
                while (m.find()) {
                    names.add(m.group(1) != null ? m.group(1) : m.group(2));
                }
            }
        }
        return names;
    }
}
