package com.forcitis.one.platform.scheduling.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** spring.autoconfigure.exclude is bound whole from the highest source, never merged: every file that sets it must keep the Moments exclusion. */
class SpringAutoconfigureExcludeGuardTest {
    static final String KEY = "spring.autoconfigure.exclude";
    static final String MOMENTS = "org.springframework.modulith.moments.autoconfigure.MomentsAutoConfiguration";

    static boolean keepsMomentsExcluded(Properties properties) {
        String value = properties.getProperty(KEY);
        return value == null || List.of(value.split("\\s*,\\s*")).contains(MOMENTS);
    }

    @Test
    @DisplayName("every application*.properties that sets spring.autoconfigure.exclude lists MomentsAutoConfiguration")
    void everyExcludeKeepsMoments() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources"))) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("application.*\\.properties")).sorted().toList()) {
                Properties properties = new Properties();
                try (InputStream in = Files.newInputStream(file)) {
                    properties.load(in);
                }
                if (!keepsMomentsExcluded(properties)) {
                    problems.add(file.getFileName() + " sets " + KEY + " without " + MOMENTS);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("injection: an exclude list without Moments is caught, one with it or none at all is not")
    void aListWithoutMomentsIsCaught() {
        Properties without = new Properties();
        without.setProperty(KEY, "com.example.OtherAutoConfiguration");
        Properties with = new Properties();
        with.setProperty(KEY, "com.example.OtherAutoConfiguration, " + MOMENTS);

        assertThat(keepsMomentsExcluded(without)).isFalse();
        assertThat(keepsMomentsExcluded(with)).isTrue();
        assertThat(keepsMomentsExcluded(new Properties())).isTrue();
    }
}
