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

/** An upload goes through the idempotency filter: a large body must reach the handler unchanged without being held in the heap. */
class SpooledBodyTest {
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

    private static long spoolFiles() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(path -> path.getFileName().toString().startsWith("idempotent-body-")).count();
        }
    }

    @Test
    @DisplayName("a small body stays in memory and a large one goes to a file; both read back identical and hash like the old whole-body hash")
    void smallInMemoryLargeOnDiskSameBytesSameHash() throws Exception {
        long before = spoolFiles();
        for (int length : new int[] {0, 1, SpooledBody.MEMORY_LIMIT, SpooledBody.MEMORY_LIMIT + 1, 3 * 1024 * 1024 + 5}) {
            byte[] content = bytes(length);
            try (SpooledBody body = SpooledBody.read(new ByteArrayInputStream(content), PREFIX, 4 * 1024 * 1024)) {
                assertThat(body.hash()).as("hash of " + length).isEqualTo(expectedHash(content));
                try (InputStream in = body.open()) {
                    assertThat(in.readAllBytes()).as("content of " + length).isEqualTo(content);
                }
                try (InputStream again = body.open()) {
                    assertThat(again.readAllBytes()).as("a second read of " + length).isEqualTo(content);
                }
                assertThat(spoolFiles() - before).as("spill file for " + length).isEqualTo(length > SpooledBody.MEMORY_LIMIT ? 1 : 0);
            }
            assertThat(spoolFiles()).as("closed body leaves no file").isEqualTo(before);
        }
    }

    @Test
    @DisplayName("a body above the limit is refused and leaves no file behind, even when it already went to disk")
    void tooLargeIsRefusedWithoutLeavingAFile() throws Exception {
        long before = spoolFiles();
        assertThatThrownBy(() -> SpooledBody.read(new ByteArrayInputStream(bytes(SpooledBody.MEMORY_LIMIT * 3)), PREFIX, SpooledBody.MEMORY_LIMIT * 2L))
                .isInstanceOf(SpooledBody.TooLarge.class);
        assertThat(spoolFiles()).isEqualTo(before);
        assertThatThrownBy(() -> SpooledBody.read(new ByteArrayInputStream(bytes(10)), PREFIX, 9)).isInstanceOf(SpooledBody.TooLarge.class);
        SpooledBody.read(new ByteArrayInputStream(bytes(10)), PREFIX, 10).close();
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
                if (given > SpooledBody.MEMORY_LIMIT * 2) {
                    throw new IOException("connection reset");
                }
                int count = Math.min(length, 8192);
                given += count;
                return count;
            }
        };
        assertThatThrownBy(() -> SpooledBody.read(failing, PREFIX, 1L << 30)).isInstanceOf(IOException.class).hasMessage("connection reset");
        assertThat(spoolFiles()).isEqualTo(before);
    }
}
