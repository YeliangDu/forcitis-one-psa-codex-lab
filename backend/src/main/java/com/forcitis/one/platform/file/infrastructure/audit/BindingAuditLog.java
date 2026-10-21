package com.forcitis.one.platform.file.infrastructure.audit;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.Classification;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Links and holds join their change; issuing a link and reading content change nothing, so they are recorded on their own. */
@Component
class BindingAuditLog implements BindingAudit {
    private final AuditLog audit;

    BindingAuditLog(AuditLog audit) {
        this.audit = audit;
    }

    @Override
    public void bound(FileId file, DocumentReference document, Classification classification, FileSubject boundBy) {
        audit.record(new AuditEntry("platform.file.stored-file.bound", actor(boundBy), null, subject(file),
                documentOf(document) + " access=" + classification.accessLevel() + " personal=" + classification.personalInformation() + by(boundBy)));
    }

    @Override
    public void held(FileId file, DocumentReference initiator, FileSubject heldBy) {
        audit.record(new AuditEntry("platform.file.stored-file.held", actor(heldBy), null, subject(file), documentOf(initiator) + by(heldBy)));
    }

    @Override
    public void downloadLinkIssued(FileId file, DocumentReference document, FileSubject requester, Duration validity) {
        audit.recordIndependently(new AuditEntry("platform.file.stored-file.download-link-issued", actor(requester), null, subject(file),
                documentOf(document) + " validity=" + validity + by(requester)));
    }

    @Override
    public void contentRead(FileId file, DocumentReference document, FileSubject requester) {
        audit.recordIndependently(new AuditEntry("platform.file.stored-file.content-read", actor(requester), null, subject(file),
                documentOf(document) + by(requester)));
    }

    @Override
    public void hashMismatch(FileId file, DocumentReference document, FileSubject requester, ContentHash expected) {
        audit.recordIndependently(new AuditEntry("platform.file.stored-file.hash-mismatch", actor(requester), null, subject(file),
                documentOf(document) + " sha256=" + expected.value() + by(requester)));
    }

    private static AuditSubject subject(FileId file) {
        return AuditSubject.of(AuditSubjectKind.STORED_FILE, file.value());
    }

    private static String documentOf(DocumentReference document) {
        return "document=" + document.documentType().value() + ":" + document.documentId().value();
    }

    private static UserId actor(FileSubject subject) {
        return subject.kind() == FileSubjectKind.USER ? new UserId(UUID.fromString(subject.id())) : null;
    }

    private static String by(FileSubject subject) {
        return subject.kind() == FileSubjectKind.USER ? "" : " by=" + subject.kind() + ":" + subject.id();
    }
}
