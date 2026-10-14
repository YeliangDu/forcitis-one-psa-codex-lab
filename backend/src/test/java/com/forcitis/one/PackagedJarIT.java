package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The jar `package` builds is what the release pipeline ships: it starts the application with java -jar and runs the replay offline. */
class PackagedJarIT {
    static Path jar;

    @BeforeAll
    static void findJar() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("target"))) {
            List<Path> jars = files.filter(f -> f.getFileName().toString().matches("forcitis-one-[0-9][^-]*\\.jar")).toList();
            assertThat(jars).as("one executable jar in target").hasSize(1);
            jar = jars.getFirst();
        }
    }

    @Test
    @DisplayName("java -jar starts OneApplication: Boot launcher, Start-Class is the application, build info is packed")
    void javaJarStartsTheApplication() throws Exception {
        try (JarFile file = new JarFile(jar.toFile())) {
            var attributes = file.getManifest().getMainAttributes();
            assertThat(attributes.getValue("Main-Class")).isEqualTo("org.springframework.boot.loader.launch.PropertiesLauncher");
            assertThat(attributes.getValue("Start-Class")).isEqualTo(OneApplication.class.getName());
            assertThat(file.getEntry("META-INF/build-info.properties")).as("build-info").isNotNull();
        }
        assertThat(OneApplication.class.getMethod("main", String[].class)).isNotNull();
    }

    @Test
    @DisplayName("the replay command runs from the same jar without Spring or a database and exits 1 on the planted DEFECT (TIMESHEET_REPLAY.md)")
    void replayRunsOfflineFromTheJar(@TempDir Path out) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(java.toString(),
                "-Dloader.main=com.forcitis.one.platform.approval.infrastructure.replay.TimesheetApproverReplayCommand",
                "-cp", jar.toString(), "org.springframework.boot.loader.launch.PropertiesLauncher",
                Path.of("src/test/resources/replay/synthetic-timesheet-export.json").toAbsolutePath().toString(), out.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("the synthetic export holds one DEFECT on purpose (TimesheetApproverReplayIT): " + output).isEqualTo(1);
        assertThat(output).contains("1 unexplained difference");
        assertThat(out.resolve("replay-report.json")).exists();
        assertThat(out.resolve("replay-report.md")).exists();
    }
}
