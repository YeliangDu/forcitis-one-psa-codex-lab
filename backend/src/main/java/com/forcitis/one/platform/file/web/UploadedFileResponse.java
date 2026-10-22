package com.forcitis.one.platform.file.web;

import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.StoreResult;
import java.util.UUID;

/** Carries no file name: the answer of an upload is kept for 24 hours under its Idempotency-Key, and a name can hold a person's name. */
record UploadedFileResponse(UUID fileId, String contentHash, long byteCount, String mediaType, FileStatus status, boolean containsMacros) {

    static UploadedFileResponse of(StoreResult stored) {
        return new UploadedFileResponse(stored.id().value(), stored.contentHash().value(), stored.size(), stored.mediaType(), stored.status(),
                stored.containsMacros());
    }
}
