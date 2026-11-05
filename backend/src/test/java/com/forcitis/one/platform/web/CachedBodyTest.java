package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An upload goes through the idempotency filter: a large body must reach the handler unchanged without being held in the heap. */
class CachedBodyTest {
    private static final String PREFIX = "POST /api/v1/platform/files?null\n";

    private static byte[] bytes(int length) {
        byte[] content = new byte[length];
        new Random(7).nextBytes(content);
        return content;
    }

    private static String expectedHash(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(PREFIX.getBytes(StandardCharsets.UTF_8));
        digest.update(content);
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Only this test's own spill directory: the JVM's temporary directory is shared with every other fork and runner on the machine. */
    @TempDir
    Path spool;

    private long spoolFiles() throws IOException {
        try (Stream<Path> files = Files.list(spool)) {
            return files.filter(path -> path.getFileName().toString().startsWith("idempotent-body-")).count();
        }
    }

    @Test
    @DisplayName("a small body stays in memory and a large one goes to a file; both read back identical and hash like the old whole-body hash")
    void smallInMemoryLargeOnDiskSameBytesSameHash() throws Exception {
        long before = spoolFiles();
        for (int length : new int[] {0, 1, CachedBody.MEMORY_LIMIT, CachedBody.MEMORY_LIMIT + 1, 3 * 1024 * 1024 + 5}) {
            byte[] content = bytes(length);
            try (CachedBody body = CachedBody.read(new ByteArrayInputStream(content), PREFIX, 4 * 1024 * 1024, spool)) {
                assertThat(body.hash()).as("hash of " + length).isEqualTo(expectedHash(content));
                try (InputStream in = body.open()) {
                    assertThat(in.readAllBytes()).as("content of " + length).isEqualTo(content);
                }
                try (InputStream again = body.open()) {
                    assertThat(again.readAllBytes()).as("a second read of " + length).isEqualTo(content);
                }
                assertThat(spoolFiles() - before).as("spill file for " + length).isEqualTo(length > CachedBody.MEMORY_LIMIT ? 1 : 0);
            }
            assertThat(spoolFiles()).as("closed body leaves no file").isEqualTo(before);
        }
    }

    @Test
    @DisplayName("a body above the limit is refused and leaves no file behind, even when it already went to disk")
    void tooLargeIsRefusedWithoutLeavingAFile() throws Exception {
        long before = spoolFiles();
        assertThatThrownBy(() -> CachedBody.read(new ByteArrayInputStream(bytes(CachedBody.MEMORY_LIMIT * 3)), PREFIX, CachedBody.MEMORY_LIMIT * 2L, spool))
                .isInstanceOf(CachedBody.TooLarge.class);
        assertThat(spoolFiles()).isEqualTo(before);
        assertThatThrownBy(() -> CachedBody.read(new ByteArrayInputStream(bytes(10)), PREFIX, 9, spool)).isInstanceOf(CachedBody.TooLarge.class);
        CachedBody.read(new ByteArrayInputStream(bytes(10)), PREFIX, 10, spool).close();
    }

    @Test
    @DisplayName("a read that fails half way leaves no file behind")
    void aFailingReadLeavesNoFile() throws Exception {
        long before = spoolFiles();
        InputStream failing = new InputStream() {
            private int given;

            @Override
            public int read() throws IOException {
                return read(new byte[1], 0, 1);
            }

            @Override
            public int read(byte[] target, int offset, int length) throws IOException {
                if (given > CachedBody.MEMORY_LIMIT * 2) {
                    throw new IOException("connection reset");
                }
                int count = Math.min(length, 8192);
                given += count;
                return count;
            }
        };
        assertThatThrownBy(() -> CachedBody.read(failing, PREFIX, 1L << 30, spool)).isInstanceOf(IOException.class).hasMessage("connection reset");
        assertThat(spoolFiles()).isEqualTo(before);
    }
}
