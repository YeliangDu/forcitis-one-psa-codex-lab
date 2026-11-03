package com.forcitis.one.platform.integration.infrastructure.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ScanOutcome;
import com.forcitis.one.platform.integration.api.ScanVerdict;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClamdScannerTest {
    static final String FRESH = "ClamAV 1.4.1/27435/Tue Oct 21 08:00:00 2026";
    static final Clock NOW = Clock.systemUTC();

    @TempDir
    Path staging;

    private Path staged() throws Exception {
        Path dir = Files.createDirectories(staging.resolve("instance-1"));
        return Files.writeString(dir.resolve("0199f0c4-0000-7000-8000-000000000001"), "content");
    }

    private MalwareScanProperties tcp(int port, int attempts, Duration scanTimeout) {
        return new MalwareScanProperties(true, null, "127.0.0.1", port, staging, Duration.ofSeconds(1), scanTimeout, Duration.ofSeconds(5),
                attempts, Duration.ofHours(48), ZoneOffset.UTC);
    }

    private static Function<String, String> answering(String scanResult) {
        return command -> command.equals("zVERSION") ? freshVersion() : command.substring("zSCAN ".length()) + ": " + scanResult;
    }

    private static String freshVersion() {
        return "ClamAV 1.4.1/27435/" + java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", java.util.Locale.ROOT)
                .format(java.time.LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
    }

    private ClamdScanner scanner(MalwareScanProperties properties) {
        return new ClamdScanner(properties, NOW);
    }

    @Test
    void aCleanFileIsScannedByPathNeverStreamed() throws Exception {
        Path file = staged();
        try (FakeClamd clamd = FakeClamd.tcp(answering("OK"))) {
            ScanVerdict verdict = scanner(tcp(clamd.port(), 1, Duration.ofSeconds(2))).scan(file);
            assertThat(verdict.outcome()).isEqualTo(ScanOutcome.CLEAN);
            assertThat(verdict.definitionsVersion()).isEqualTo("27435");
            assertThat(clamd.commands).containsExactly("zVERSION", "zSCAN " + file.toRealPath());
            assertThat(clamd.commands).noneMatch(c -> c.contains("INSTREAM"));
        }
    }

    @Test
    void anInfectedFileCarriesItsThreatName() throws Exception {
        Path file = staged();
        try (FakeClamd clamd = FakeClamd.tcp(answering("Eicar-Signature FOUND"))) {
            ScanVerdict verdict = scanner(tcp(clamd.port(), 1, Duration.ofSeconds(2))).scan(file);
            assertThat(verdict.outcome()).isEqualTo(ScanOutcome.INFECTED);
            assertThat(verdict.threatName()).isEqualTo("Eicar-Signature");
        }
    }

    @Test
    void aScanThatDoesNotFinishIsUnavailableAndNotRetried() throws Exception {
        Path file = staged();
        for (String result : new String[] {"Heuristics.Limits.Exceeded.MaxFileSize FOUND", "Heuristics.Limits.Exceeded.MaxRecursion FOUND",
                "lstat() failed: Permission denied. ERROR", "something unexpected"}) {
            try (FakeClamd clamd = FakeClamd.tcp(answering(result))) {
                assertThatThrownBy(() -> scanner(tcp(clamd.port(), 3, Duration.ofSeconds(2))).scan(file))
                        .isInstanceOf(ExternalSystemUnavailableException.class);
                assertThat(clamd.commands).containsExactly("zVERSION", "zSCAN " + file.toRealPath());
            }
        }
    }

    @Test
    void outdatedDefinitionsAreUnavailable() throws Exception {
        Path file = staged();
        try (FakeClamd clamd = FakeClamd.tcp(command -> command.equals("zVERSION") ? FRESH : command.substring(6) + ": OK")) {
            Clock later = Clock.fixed(Instant.parse("2026-10-23T08:00:01Z"), ZoneOffset.UTC);
            assertThatThrownBy(() -> new ClamdScanner(tcp(clamd.port(), 3, Duration.ofSeconds(2)), later).scan(file))
                    .isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("definitions");
            assertThat(clamd.commands).containsExactly("zVERSION");
        }
    }

    @Test
    void aCutOffOrMultiLineReplyIsUnavailableAndNotRetried() throws Exception {
        Path file = staged();
        for (Function<String, String> replies : java.util.List.<Function<String, String>>of(
                command -> command.equals("zVERSION") ? freshVersion() : FakeClamd.UNTERMINATED + command.substring(6) + ": OK",
                command -> command.equals("zVERSION") ? freshVersion() : command.substring(6) + ": Heuristics.Encrypted.Zip FOUND\n"
                        + command.substring(6) + ": Eicar-Signature FOUND")) {
            try (FakeClamd clamd = FakeClamd.tcp(replies)) {
                assertThatThrownBy(() -> scanner(tcp(clamd.port(), 3, Duration.ofSeconds(2))).scan(file))
                        .isInstanceOf(ExternalSystemUnavailableException.class);
                assertThat(clamd.commands).hasSize(2);
            }
        }
    }

    @Test
    void definitionsBuiltInTheFutureMeanAWrongZoneAndAreUnavailable() throws Exception {
        Path file = staged();
        try (FakeClamd clamd = FakeClamd.tcp(command -> command.equals("zVERSION") ? FRESH : command.substring(6) + ": OK")) {
            Clock earlier = Clock.fixed(Instant.parse("2026-10-21T07:00:00Z"), ZoneOffset.UTC);
            assertThatThrownBy(() -> new ClamdScanner(tcp(clamd.port(), 1, Duration.ofSeconds(2)), earlier).scan(file))
                    .isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("clamd-zone");
        }
    }

    @Test
    void noDefinitionsLoadedIsUnavailable() throws Exception {
        Path file = staged();
        try (FakeClamd clamd = FakeClamd.tcp(command -> "ClamAV 1.4.1")) {
            assertThatThrownBy(() -> scanner(tcp(clamd.port(), 1, Duration.ofSeconds(2))).scan(file))
                    .isInstanceOf(ExternalSystemUnavailableException.class);
        }
    }

    @Test
    void aSilentClamdTimesOutAndIsRetriedWithinTheLimitThenRefused() throws Exception {
        Path file = staged();
        // A frozen clock: the 200 ms is then spent only waiting on the silent server (the selector waits in real time), never on a stall of the test JVM
        // before the client has written its command (a deadline read from the live clock expired first on a loaded host: the server then saw an empty command)
        Clock frozen = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        try (FakeClamd clamd = FakeClamd.tcp(command -> null)) {
            assertThatThrownBy(() -> new ClamdScanner(tcp(clamd.port(), 2, Duration.ofMillis(200)), frozen).scan(file))
                    .isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("2 attempts");
            assertThat(clamd.commands).containsExactly("zVERSION", "zVERSION");
        }
    }

    @Test
    void anUnreachableClamdIsRefused() throws Exception {
        Path file = staged();
        int port;
        try (FakeClamd clamd = FakeClamd.tcp(answering("OK"))) {
            port = clamd.port();
        }
        int closedPort = port;
        assertThatThrownBy(() -> scanner(tcp(closedPort, 2, Duration.ofSeconds(1))).scan(file))
                .isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    void anAnswerForAnotherFileIsUnavailable() throws Exception {
        Path file = staged();
        try (FakeClamd clamd = FakeClamd.tcp(command -> command.equals("zVERSION") ? freshVersion() : "/elsewhere: OK")) {
            assertThatThrownBy(() -> scanner(tcp(clamd.port(), 1, Duration.ofSeconds(2))).scan(file))
                    .isInstanceOf(ExternalSystemUnavailableException.class);
        }
    }

    @Test
    void onlyRegularFilesUnderTheStagingRootAreScanned() throws Exception {
        Path file = staged();
        try (FakeClamd clamd = FakeClamd.tcp(answering("OK"))) {
            ClamdScanner scanner = scanner(tcp(clamd.port(), 1, Duration.ofSeconds(2)));
            Path outside = Files.writeString(Files.createTempFile("outside", ".bin"), "x");
            try {
                assertThatThrownBy(() -> scanner.scan(outside)).isInstanceOf(IllegalArgumentException.class);
            } finally {
                Files.delete(outside);
            }
            assertThatThrownBy(() -> scanner.scan(staging.resolve("instance-1/../../etc/passwd"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> scanner.scan(staging.resolve("missing"))).isInstanceOf(IllegalArgumentException.class);
            Path link = Files.createSymbolicLink(staging.resolve("instance-1/link"), file);
            assertThatThrownBy(() -> scanner.scan(link)).isInstanceOf(IllegalArgumentException.class);
            Path elsewhere = Files.createTempDirectory("elsewhere");
            try {
                Files.writeString(elsewhere.resolve("f"), "x");
                Files.createSymbolicLink(staging.resolve("through"), elsewhere);
                assertThatThrownBy(() -> scanner.scan(staging.resolve("through/f"))).isInstanceOf(IllegalArgumentException.class);
            } finally {
                Files.deleteIfExists(staging.resolve("through"));
                Files.deleteIfExists(elsewhere.resolve("f"));
                Files.deleteIfExists(elsewhere);
            }
            assertThat(clamd.commands).isEmpty();
        }
    }

    @Test
    void aUnixSocketOnThisHostIsTheSameProtocol() throws Exception {
        Path file = staged();
        Path socket = Files.createTempDirectory(Path.of("/tmp"), "clamd").resolve("s");
        try (FakeClamd clamd = FakeClamd.unix(socket, answering("OK"))) {
            MalwareScanProperties properties = new MalwareScanProperties(true, socket, null, null, staging, Duration.ofSeconds(1),
                    Duration.ofSeconds(2), Duration.ofSeconds(5), 1, Duration.ofHours(48), ZoneId.of("UTC"));
            assertThat(new ClamdScanner(properties, NOW).scan(file).outcome()).isEqualTo(ScanOutcome.CLEAN);
        } finally {
            Files.deleteIfExists(socket);
            Files.deleteIfExists(socket.getParent());
        }
    }
}
