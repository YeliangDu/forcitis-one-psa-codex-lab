package com.forcitis.one.platform.integration.api;

import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The business file bucket. Writes never overwrite and are read back against size, MD5 and the SHA-256 (sha256Hex); a mismatch or any
 * store failure throws ExternalSystemUnavailableException. Staging and quarantine keys are never presigned or read through the formal path
 * (ADR-0070 section 7); each kind of access runs under its own identity.
 */
public interface BusinessFileStore {

    /** Into a staging or quarantine key only. */
    void put(StoredObjectKey key, Path source, String sha256Hex, long size);

    /** Server-side copy from a staging key to the formal key of the same file, then read back. */
    void promote(StoredObjectKey staging, StoredObjectKey formal, String sha256Hex, long size);

    /** Formal keys only; the link forces an attachment download with the given name and media type. */
    URI presignDownload(StoredObjectKey formal, Duration validity, String fileName, String mediaType);

    /** Formal keys under the application identity, quarantine keys under the quarantine reader identity; staging keys are refused. */
    void read(StoredObjectKey key, OutputStream sink);
}
