package com.forcitis.one.platform.file.api;

public enum ImportRejectionReason {
    TOO_LARGE,
    MEDIA_TYPE_NOT_ALLOWED,
    ENCRYPTED,
    QUARANTINED,
    SCAN_UNAVAILABLE,
    SPI_NOT_ACCEPTED
}
