package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.Lineage;
import java.time.Instant;
import java.util.Objects;

/** A file row as it is written once the scan decided (ADR-0070 section 2); threatName only for QUARANTINED or a release by exception. */
public record NewStoredFile(FileId id, String objectKey, ContentHash contentHash, long size, String mediaType, FileSubject uploader,
                            FileStatus status, boolean containsMacros, Lineage lineage, String engineVersion, String definitionsVersion,
                            String threatName, boolean releasedByException, String originalName, Instant storedAt) {

    public NewStoredFile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(uploader, "uploader");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(lineage, "lineage");
        Objects.requireNonNull(engineVersion, "engineVersion");
        Objects.requireNonNull(definitionsVersion, "definitionsVersion");
        Objects.requireNonNull(originalName, "originalName");
        Objects.requireNonNull(storedAt, "storedAt");
        if (status != FileStatus.AVAILABLE && status != FileStatus.QUARANTINED) {
            throw new IllegalArgumentException("a file starts AVAILABLE or QUARANTINED");
        }
    }

    /** The scan decided: infected content not on the exception list is quarantined, anything else that got this far is available. */
    public static FileStatus decidedBy(boolean infected, boolean releasedByException) {
        return infected && !releasedByException ? FileStatus.QUARANTINED : FileStatus.AVAILABLE;
    }
}
