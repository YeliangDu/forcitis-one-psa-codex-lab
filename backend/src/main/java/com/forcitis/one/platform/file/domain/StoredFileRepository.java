package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import java.time.Instant;
import java.util.List;
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

    /** Available files of one kind of uploader that no document has linked and that were stored before cutoff, oldest first. */
    List<FileId> unboundStoredBefore(FileSubjectKind uploaderKind, Instant cutoff, int limit);

    /** Locks the file for the lifecycle change; empty when there is none. */
    Optional<StoredFile> lockForExpiry(FileId file);

    /** Writes the status change of a locked file and its state transition by the system job that made it; joins the caller's transaction. */
    void saveExpiry(StoredFile file, Instant at);
}
