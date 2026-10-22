package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileStatus;

/** A file as linked to one document: what a download link or a content read needs. originalName is personal data. */
public record BoundContent(FileStatus status, String objectKey, ContentHash contentHash, long size, String mediaType, String originalName) {
}
