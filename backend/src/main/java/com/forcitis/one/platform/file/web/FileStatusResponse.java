package com.forcitis.one.platform.file.web;

import com.forcitis.one.platform.file.api.FileStatus;
import java.util.UUID;

record FileStatusResponse(UUID fileId, FileStatus status) {
}
