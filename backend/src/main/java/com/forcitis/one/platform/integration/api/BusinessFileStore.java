package com.forcitis.one.platform.integration.api;

import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The business file bucket. Writes never overwrite and are read back against size, MD5 and the SHA-256 (sha256Hex); a mismatch or any
 * store failure throws ExternalSystemUnavailableException (a failing caller sink in read is no store failure and passes through as thrown). Staging and quarantine keys are never presigned or read through the formal path
 * (ADR-0070 section 7); each kind of access runs under its own identity.
 */
public interface BusinessFileStore {

    /** Into a staging or quarantine key only. */
    void put(StoredObjectKey key, Path source, String sha256Hex, long size);

    /**
     * Server-side copy from a staging key, itself checked against size and SHA-256 first, to the formal key of the same file, then read
     * back. A formal object already there with the same size and SHA-256 counts as done (a retry), also when the staging key has expired
     * meanwhile; any other is refused, never overwritten.
     */
    void promote(StoredObjectKey staging, StoredObjectKey formal, String sha256Hex, long size);

    /** The longest validity presignDownload signs; the file service checks its own ceiling against it at start-up. */
    Duration maxLinkValidity();

    /** Formal keys only; the link forces an attachment download with the given name and media type. */
    URI presignDownload(StoredObjectKey formal, Duration validity, String fileName, String mediaType);

    /**
     * Formal keys under the application identity, quarantine keys under the quarantine reader identity; staging keys are refused. This
     * checks the key only: that only the security role reads quarantine, on an approved request and audited, is the file service's
     * quarantine read use case (ADR-0070 section 6 item 2), not the RAM policy alone.
     */
    void read(StoredObjectKey key, OutputStream sink);
}
