package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileSubject;

/** Every decision of an upload is audited (ADR-0070 section 2 item 4); refusals that create no file survive the request's rollback. */
public interface StoreAudit {

    void stored(NewStoredFile file);

    void quarantined(NewStoredFile file);

    /** No original name until the user settles ADR-0060 against ADR-0070 (one-architecture 2026-10-21): the stricter rule holds. */
    void encryptedRefused(FileSubject uploader, ContentHash contentHash, long size, String mediaType);

    /** Infected content whose quarantine write or row failed: the threat is still on record. */
    void quarantineFailed(FileSubject uploader, ContentHash contentHash, String threatName);

    /** contentHash is null when the content was not fully read. */
    void unavailable(FileSubject uploader, ContentHash contentHash, UnavailableCause cause);

    enum UnavailableCause {
        SCANNER,
        OBJECT_STORE,
        STAGING
    }
}
