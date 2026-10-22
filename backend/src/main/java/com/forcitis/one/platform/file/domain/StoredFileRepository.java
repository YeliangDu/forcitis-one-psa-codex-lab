package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository {

    /** Writes the file, its original name and its first state transition; joins the caller's transaction. */
    void insert(NewStoredFile file);

    Optional<LineageTarget> lineageTarget(FileId file);

    /** On the false-positive exception list and not revoked (ADR-0070 section 6 item 3). */
    boolean hasScanException(ContentHash contentHash);

    /** The status of a file that this user uploaded and that no document has linked yet; empty for any other file, so nobody learns that it exists. */
    Optional<FileStatus> unboundStatusStoredBy(FileId file, UUID uploader);
}
