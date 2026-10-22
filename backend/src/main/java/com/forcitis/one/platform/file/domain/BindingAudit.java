package com.forcitis.one.platform.file.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubject;
import java.time.Duration;

/** ADR-0060 section 2: linking, holding, issuing a link and reading content are audited; no original name and no link in the details. */
public interface BindingAudit {

    void bound(FileId file, DocumentReference document, Classification classification, FileSubject boundBy);

    void held(FileId file, DocumentReference initiator, FileSubject heldBy);

    void downloadLinkIssued(FileId file, DocumentReference document, FileSubject requester, Duration validity);

    void contentRead(FileId file, DocumentReference document, FileSubject requester);

    /** Survives the caller's rollback: the stored content no longer matches its hash. */
    void hashMismatch(FileId file, DocumentReference document, FileSubject requester, ContentHash expected);
}
