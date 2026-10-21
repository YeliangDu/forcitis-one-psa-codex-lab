package com.forcitis.one.platform.file.infrastructure.audit;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.domain.NewStoredFile;
import com.forcitis.one.platform.file.domain.StoreAudit;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Details carry own ids, the SHA-256, engine and definitions versions and fixed codes only: never the original name or a link
 * (ADR-0060 section 2), never an exception text (TB-01 row 22).
 */
@Component
class StoreAuditLog implements StoreAudit {
    private final AuditLog audit;

    StoreAuditLog(AuditLog audit) {
        this.audit = audit;
    }

    @Override
    public void stored(NewStoredFile file) {
        AuditSubject subject = AuditSubject.of(AuditSubjectKind.STORED_FILE, file.id().value());
        if (file.releasedByException()) {
            audit.record(new AuditEntry("platform.file.stored-file.released-by-exception", actor(file.uploader()), null, subject, describe(file)));
        } else {
            audit.record(new AuditEntry("platform.file.stored-file.stored", actor(file.uploader()), null, subject, describe(file)));
        }
    }

    @Override
    public void quarantined(NewStoredFile file) {
        audit.record(new AuditEntry("platform.file.stored-file.quarantined", actor(file.uploader()), null,
                AuditSubject.of(AuditSubjectKind.STORED_FILE, file.id().value()), describe(file)));
    }

    @Override
    public void encryptedRefused(FileSubject uploader, ContentHash contentHash, long size, String mediaType) {
        audit.recordIndependently(new AuditEntry("platform.file.upload.encrypted-refused", actor(uploader), null,
                AuditSubject.without(AuditSubjectKind.REQUEST),
                "reason=ENCRYPTED sha256=" + contentHash.value() + " bytes=" + size + " media=" + mediaType + uploaderOf(uploader)));
    }

    @Override
    public void quarantineFailed(FileSubject uploader, ContentHash contentHash, String threatName) {
        audit.recordIndependently(new AuditEntry("platform.file.upload.quarantine-failed", actor(uploader), null, AuditSubject.without(AuditSubjectKind.REQUEST),
                "reason=QUARANTINE_FAILED sha256=" + contentHash.value() + " threat=" + threatName + uploaderOf(uploader)));
    }

    @Override
    public void unavailable(FileSubject uploader, ContentHash contentHash, UnavailableCause cause) {
        audit.recordIndependently(new AuditEntry("platform.file.upload.unavailable", actor(uploader), null, AuditSubject.without(AuditSubjectKind.REQUEST),
                "reason=" + cause + "_UNAVAILABLE" + (contentHash == null ? "" : " sha256=" + contentHash.value()) + uploaderOf(uploader)));
    }

    private static String describe(NewStoredFile file) {
        return "sha256=" + file.contentHash().value() + " engine=" + file.engineVersion() + " definitions=" + file.definitionsVersion()
                + (file.threatName() == null ? "" : " threat=" + file.threatName()) + uploaderOf(file.uploader());
    }

    private static UserId actor(FileSubject subject) {
        return subject.kind() == FileSubjectKind.USER ? new UserId(UUID.fromString(subject.id())) : null;
    }

    private static String uploaderOf(FileSubject subject) {
        return subject.kind() == FileSubjectKind.USER ? "" : " uploader=" + subject.kind() + ":" + subject.id();
    }
}
