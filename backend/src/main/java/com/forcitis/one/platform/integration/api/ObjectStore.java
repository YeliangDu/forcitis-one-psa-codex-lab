package com.forcitis.one.platform.integration.api;

import java.nio.file.Path;

/** Write-once storage for archives. Failures surface as ExternalSystemUnavailableException; nothing is deleted anywhere before put has returned. */
public interface ObjectStore {
    /** Stores the file under the key, never over an existing different object, and returns what the store holds afterwards. */
    ArchivedObject put(String key, Path file, String sha256);

    /** Checks that the store can be written to and is configured as required, without leaving an object behind. */
    void probe();
}
