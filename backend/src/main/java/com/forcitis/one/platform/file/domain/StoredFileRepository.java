package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import java.util.Optional;

public interface StoredFileRepository {

    /** Writes the file, its original name and its first state transition; joins the caller's transaction. */
    void insert(NewStoredFile file);

    Optional<LineageTarget> lineageTarget(FileId file);

    /** On the false-positive exception list and not revoked (ADR-0070 section 6 item 3). */
    boolean hasScanException(ContentHash contentHash);
}
