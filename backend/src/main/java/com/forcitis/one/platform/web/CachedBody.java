package com.forcitis.one.platform.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The request body of an idempotent write, read once: the first bytes stay in memory, a larger body goes to a temporary file, so a file
 * upload never sits in the heap. The hash of the request (method, address, then the body) is computed while reading. The file is removed on
 * close; a body above the limit is refused before more than the limit has been written.
 */
final class CachedBody implements Closeable {
    static final int MEMORY_LIMIT = 64 * 1024;

    /** The body is longer than the limit the filter accepts. */
    static final class TooLarge extends IOException {
        TooLarge(long limit) {
            super("the request body is longer than " + limit + " bytes");
        }
    }

    private final byte[] memory;
    private final Path file;
    private final String hash;

    private CachedBody(byte[] memory, Path file, String hash) {
        this.memory = memory;
        this.file = file;
        this.hash = hash;
    }

    /** prefix is what is hashed before the body, so the same bytes sent elsewhere hash differently. */
    static CachedBody read(InputStream in, String prefix, long limit) throws IOException {
        return read(in, prefix, limit, null);
    }

    /** spoolDirectory is where a large body goes; null is the JVM's temporary directory. */
    static CachedBody read(InputStream in, String prefix, long limit, Path spoolDirectory) throws IOException {
        MessageDigest digest = sha256();
        digest.update(prefix.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        Path spill = null;
        OutputStream spillOut = null;
        long size = 0;
        try {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                size += n;
                if (size > limit) {
                    throw new TooLarge(limit);
                }
                digest.update(chunk, 0, n);
                if (spillOut == null && size <= MEMORY_LIMIT) {
                    head.write(chunk, 0, n);
                    continue;
                }
                if (spillOut == null) {
                    spill = spoolDirectory == null ? Files.createTempFile("idempotent-body-", ".part") : Files.createTempFile(spoolDirectory, "idempotent-body-", ".part");
                    spillOut = Files.newOutputStream(spill);
                    head.writeTo(spillOut);
                    head.reset();
                }
                spillOut.write(chunk, 0, n);
            }
            if (spillOut != null) {
                spillOut.close();
            }
        } catch (IOException | RuntimeException failure) {
            if (spillOut != null) {
                try {
                    spillOut.close();
                } catch (IOException closing) {
                    failure.addSuppressed(closing);
                }
            }
            if (spill != null) {
                Files.deleteIfExists(spill);
            }
            throw failure;
        }
        return new CachedBody(spill == null ? head.toByteArray() : null, spill, HexFormat.of().formatHex(digest.digest()));
    }

    String hash() {
        return hash;
    }

    InputStream open() throws IOException {
        return file == null ? new ByteArrayInputStream(memory) : Files.newInputStream(file);
    }

    @Override
    public void close() throws IOException {
        if (file != null) {
            Files.deleteIfExists(file);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
