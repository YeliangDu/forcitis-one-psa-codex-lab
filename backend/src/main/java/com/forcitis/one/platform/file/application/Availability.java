package com.forcitis.one.platform.file.application;

import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileStatus;

final class Availability {

    private Availability() {
    }

    static void require(FileStatus status) {
        switch (status) {
            case AVAILABLE -> {
            }
            case QUARANTINED -> throw FileError.PLATFORM_FILE_QUARANTINED.refusal("the file did not pass the malware check");
            case EXPIRED_UNBOUND -> throw FileError.PLATFORM_FILE_NOT_AVAILABLE.refusal("the file was not linked in time and has expired");
            case DISPOSED -> throw FileError.PLATFORM_FILE_DISPOSED.refusal("the file has been disposed of");
        }
    }
}
