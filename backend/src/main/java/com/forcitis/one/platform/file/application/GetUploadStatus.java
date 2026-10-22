package com.forcitis.one.platform.file.application;

import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import java.util.UUID;

/**
 * The scan state of an upload, for the person who made it and only until a document links it (ADR-0060 section 6 item 1). A file of
 * someone else, a linked file and a missing one all look the same: not found.
 */
public final class GetUploadStatus {
    private final StoredFileRepository files;

    public GetUploadStatus(StoredFileRepository files) {
        this.files = files;
    }

    public FileStatus execute(FileId file, UUID uploader) {
        return files.unboundStatusUploadedBy(file, uploader)
                .orElseThrow(() -> FileError.PLATFORM_FILE_NOT_FOUND.refusal("no unlinked upload " + file + " of this user"));
    }
}
