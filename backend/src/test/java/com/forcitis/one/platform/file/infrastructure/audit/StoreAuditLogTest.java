package com.forcitis.one.platform.file.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.domain.NewStoredFile;
import com.forcitis.one.platform.file.domain.StoreAudit.UnavailableCause;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The original name, personal data, is written for the encrypted refusal only (user decision, intent 2026-10-21-170547). */
class StoreAuditLogTest {
    static final ContentHash HASH = new ContentHash("a".repeat(64));
    static final FileSubject USER = new FileSubject(FileSubjectKind.USER, "01900000-0000-7000-8000-00000000000a");

    final List<AuditEntry> recorded = new ArrayList<>();
    final StoreAuditLog log = new StoreAuditLog(new AuditLog() {
        @Override
        public AuditEntryId record(AuditEntry entry) {
            recorded.add(entry);
            return null;
        }

        @Override
        public void recordIndependently(AuditEntry entry) {
            recorded.add(entry);
        }
    });

    @Test
    void anEncryptedRefusalNamesTheFileWithoutControlCharactersOrSecrets() {
        log.encryptedRefused(USER, HASH, 7, "application/pdf", "工资表\n.pdf");
        log.encryptedRefused(USER, HASH, 7, "application/pdf", "password=hunter2.zip");
        assertThat(recorded).extracting(AuditEntry::detail).containsExactly(
                "reason=ENCRYPTED sha256=" + HASH.value() + " bytes=7 media=application/pdf name=工资表?.pdf",
                "reason=ENCRYPTED sha256=" + HASH.value() + " bytes=7 media=application/pdf name=[withheld: looks like a secret]");
        assertThat(recorded).allSatisfy(e -> assertThat(e.subject().kind()).isEqualTo(AuditSubjectKind.REQUEST));
    }

    @Test
    void noOtherEntryCarriesTheOriginalName() {
        NewStoredFile file = new NewStoredFile(new FileId(UUID.randomUUID()), "dev/files/2026/10/x", HASH, 7, "application/pdf", USER,
                FileStatus.AVAILABLE, false, new Lineage.FirstVersion(), "ClamAV 1.4.1", "27435", null, false, "工资表.pdf", Instant.EPOCH);
        log.stored(file);
        log.unavailable(USER, HASH, UnavailableCause.SCANNER);
        log.quarantineFailed(USER, HASH, "Eicar-Signature");
        assertThat(recorded).extracting(AuditEntry::detail).noneMatch(d -> d.contains("工资表") || d.contains("name="));
    }
}
