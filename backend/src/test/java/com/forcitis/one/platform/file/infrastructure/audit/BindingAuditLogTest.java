package com.forcitis.one.platform.file.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.file.api.AccessLevel;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.PersonalInformationMark;
import com.forcitis.one.platform.file.domain.Classification;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-0060 section 2: the details name the document and the subject, never a file name or a link; only link and content reads stand alone. */
class BindingAuditLogTest {
    static final FileId FILE = new FileId(UUID.fromString("0199f0c4-0000-7000-8000-000000000001"));
    static final DocumentReference CONTRACT = new DocumentReference(new DocumentTypeCode("CONTRACT"),
            new DocumentId(UUID.fromString("0199f0c4-0000-7000-8000-000000000002")));
    static final FileSubject USER = new FileSubject(FileSubjectKind.USER, "01900000-0000-7000-8000-00000000000a");
    static final FileSubject SIGNING = new FileSubject(FileSubjectKind.PLATFORM_MODULE, "platform.signing");

    final List<String> recorded = new ArrayList<>();
    final BindingAuditLog log = new BindingAuditLog(new AuditLog() {
        @Override
        public AuditEntryId record(AuditEntry entry) {
            recorded.add("joined " + entry.action() + " " + entry.detail());
            return null;
        }

        @Override
        public void recordIndependently(AuditEntry entry) {
            recorded.add("own " + entry.action() + " " + entry.detail());
        }
    });

    @Test
    void eachDecisionIsRecordedWithItsDocument() {
        log.bound(FILE, CONTRACT, new Classification(AccessLevel.SENSITIVE, PersonalInformationMark.PI), USER);
        log.held(FILE, CONTRACT, USER);
        log.downloadLinkIssued(FILE, CONTRACT, USER, Duration.ofMinutes(5));
        log.contentRead(FILE, CONTRACT, SIGNING);
        log.hashMismatch(FILE, CONTRACT, SIGNING, new ContentHash("a".repeat(64)));
        String document = "document=CONTRACT:" + CONTRACT.documentId();
        assertThat(recorded).containsExactly(
                "joined platform.file.stored-file.bound " + document + " access=SENSITIVE personal=PI",
                "joined platform.file.stored-file.held " + document,
                "own platform.file.stored-file.download-link-issued " + document + " validity=PT5M",
                "own platform.file.stored-file.content-read " + document + " by=PLATFORM_MODULE:platform.signing",
                "own platform.file.stored-file.hash-mismatch " + document + " sha256=" + "a".repeat(64) + " by=PLATFORM_MODULE:platform.signing");
    }
}
