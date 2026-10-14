package com.forcitis.one.platform.integration.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ArchivedObject;
import com.forcitis.one.platform.integration.api.ObjectStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObjectStoreArchiveVaultTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T01:02:03Z"), ZoneOffset.UTC);

    @TempDir
    Path local;

    static final class MemoryStore implements ObjectStore {
        final List<String> keys = new ArrayList<>();
        RuntimeException failure;
        boolean lieAboutTheHash;
        boolean lieAboutTheSize;
        int probes;

        @Override
        public ArchivedObject put(String key, Path file, String sha256) {
            if (failure != null) {
                throw failure;
            }
            keys.add(key);
            try {
                return new ArchivedObject(key, lieAboutTheSize ? 1 : Files.size(file), lieAboutTheHash ? "f".repeat(64) : sha256);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void probe() {
            probes++;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private final MemoryStore store = new MemoryStore();

    private ObjectStoreArchiveVault vault() {
        return new ObjectStoreArchiveVault(store, local, "one-test", CLOCK);
    }

    private List<Path> files(String kind) throws IOException {
        try (Stream<Path> files = Files.list(local.resolve(kind))) {
            return files.sorted().toList();
        }
    }

    @Test
    @DisplayName("an archive is one immutable object named by kind, time and hash; the newest local copy of the kind is the only file left")
    void storesAndKeepsOnlyTheNewestLocalCopy() throws Exception {
        ArchivedObject first = vault().archive("process", List.of("{\"a\":1}", "{\"a\":2}"));
        ArchivedObject second = vault().archive("process", List.of("{\"a\":3}"));

        assertThat(first.key()).startsWith("one-test/process/2026/process-20261015T010203Z-").endsWith(".ndjson.gz");
        assertThat(second.key()).isNotEqualTo(first.key());
        assertThat(store.keys).containsExactly(first.key(), second.key());
        assertThat(files("process")).singleElement().satisfies(file -> {
            assertThat(file.getFileName().toString()).isEqualTo("latest.ndjson.gz");
            try (var in = new GZIPInputStream(Files.newInputStream(file))) {
                assertThat(new String(in.readAllBytes())).isEqualTo("{\"a\":3}\n");
            }
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))).isEqualTo(second.sha256());
        });
    }

    @Test
    @DisplayName("the disk cannot be written: the call fails and the store is not asked")
    void diskFailure() throws IOException {
        Files.writeString(local.resolve("process"), "a file where the directory should be");

        assertThatThrownBy(() -> vault().archive("process", List.of("{}"))).isInstanceOf(UncheckedIOException.class);
        assertThat(store.keys).isEmpty();
    }

    @Test
    @DisplayName("the upload fails: the call fails, no local copy replaces the previous one and no pending file stays")
    void uploadFailure() throws Exception {
        vault().archive("audit", List.of("{\"keep\":true}"));
        Path before = files("audit").getFirst();
        byte[] kept = Files.readAllBytes(before);
        store.failure = new ExternalSystemUnavailableException("OSS is down");

        assertThatThrownBy(() -> vault().archive("audit", List.of("{\"new\":true}"))).isInstanceOf(ExternalSystemUnavailableException.class);

        assertThat(files("audit")).singleElement().isEqualTo(before);
        assertThat(Files.readAllBytes(before)).isEqualTo(kept);
    }

    @Test
    @DisplayName("the store confirms another hash or size than was sent: the call fails")
    void checksumMismatch() {
        store.lieAboutTheHash = true;
        assertThatThrownBy(() -> vault().archive("process", List.of("{}"))).isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("SHA-256");
        store.lieAboutTheHash = false;
        store.lieAboutTheSize = true;
        assertThatThrownBy(() -> vault().archive("process", List.of("{}"))).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    @DisplayName("the probe writes and removes a local file, clears leftovers and probes the store; an unwritable disk or a failing store fails it")
    void probe() throws Exception {
        Files.createDirectories(local.resolve("process"));
        Files.writeString(local.resolve("process").resolve("pending-leftover.ndjson.gz"), "x");

        vault().probe("process");

        assertThat(files("process")).isEmpty();
        assertThat(store.probes).isEqualTo(1);
        store.failure = new ExternalSystemUnavailableException("no write permission");
        assertThatThrownBy(() -> vault().probe("process")).isInstanceOf(ExternalSystemUnavailableException.class);
        Files.writeString(local.resolve("audit"), "blocked");
        assertThatThrownBy(() -> vault().probe("audit")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("an archive kind is lower-case letters, so it cannot walk out of the archive directory")
    void kindIsChecked() {
        assertThatThrownBy(() -> vault().archive("../x", List.of("{}"))).isInstanceOf(IllegalArgumentException.class);
    }
}
