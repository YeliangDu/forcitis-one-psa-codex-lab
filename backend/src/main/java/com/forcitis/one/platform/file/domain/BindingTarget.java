package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import java.util.Optional;

/** The locked file row as far as linking it needs; bound says whether any document has it yet. */
public record BindingTarget(FileId id, FileStatus status, FileSubject uploader, Optional<FileId> previous, String objectKey, ContentHash contentHash,
                            long size, boolean bound) {
}
