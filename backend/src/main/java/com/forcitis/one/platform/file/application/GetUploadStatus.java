package com.forcitis.one.platform.file.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.domain.StoredFileRepository;

/**
 * The scan state of an upload, for the person who made it and only until a document links it (ADR-0060 section 6 item 1). A file of
 * someone else, a linked file and a missing one all look the same: not found.
 */
public final class GetUploadStatus {
    private final StoredFileRepository files;
    private final CurrentUser currentUser;

    public GetUploadStatus(StoredFileRepository files, CurrentUser currentUser) {
        this.files = files;
        this.currentUser = currentUser;
    }

    public FileStatus execute(FileId file) {
        return files.unboundStatusStoredBy(file, currentUser.userId().value())
                .orElseThrow(() -> FileError.PLATFORM_FILE_NOT_FOUND.refusal("no unlinked upload " + file + " of this user"));
    }
}
