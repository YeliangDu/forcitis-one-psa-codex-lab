package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.FileStatus;

/** What a new file's previous version or source is, as far as declaring the lineage needs. */
public record LineageTarget(FileStatus status, boolean hasSuccessor) {
}
