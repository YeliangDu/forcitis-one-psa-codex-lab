package com.forcitis.one.platform.file.api;

/** UNAVAILABLE covers the scanner and the object store (ADR-0070 section 2 item 5); it is a rejection too, counted in the tie-out. */
public enum ImportRejectionReason {
    TOO_LARGE,
    MEDIA_TYPE_NOT_ALLOWED,
    ENCRYPTED,
    QUARANTINED,
    UNAVAILABLE,
    SPI_NOT_ACCEPTED,
    PRESERVATION_CATEGORY_UNKNOWN
}
