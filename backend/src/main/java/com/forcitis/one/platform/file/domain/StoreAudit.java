package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileSubject;

/** Every decision of an upload is audited (ADR-0070 section 2 item 4); refusals that create no file survive the request's rollback. */
public interface StoreAudit {

    void stored(NewStoredFile file);

    void quarantined(NewStoredFile file);

    void encryptedRefused(FileSubject uploader, ContentHash contentHash, String originalName);

    /** contentHash is null when the content was not fully read. */
    void unavailable(FileSubject uploader, ContentHash contentHash, String reason);
}
