package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.ChangeChannel;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The audit entries of role versions, written in the transaction that opens or closes the version so that the version row can carry the
 * entry's id (ADR-0034 decision 1 item 2; ADR-0034-IMPL ruling A-6). The detail holds ids, enum names and the clock reading only (card C4):
 * the version's effective time may be later than the clock (greatest(:now, ...)), so the actual reading is kept here.
 */
@Component
class RoleVersionAudit {

    private final AuditLog audit;

    RoleVersionAudit(AuditLog audit) {
        this.audit = audit;
    }

    AuditEntryId opened(RoleId roleId, RoleVersionId versionId, int versionNumber, ChangeChannel channel, @Nullable Integer declarationRevisionNumber,
                        @Nullable UserId recordedBy, Instant effectiveFrom, Instant clockReading) {
        return audit.record(new AuditEntry("platform.identity.role-version.opened", recordedBy, null, AuditSubject.of(AuditSubjectKind.ROLE, roleId.value()),
                "roleVersionId=" + versionId.value() + " versionNumber=" + versionNumber + " channel=" + channel.name()
                        + " declarationRevisionNumber=" + (declarationRevisionNumber == null ? "none" : declarationRevisionNumber)
                        + " effectiveFrom=" + effectiveFrom + " clock=" + clockReading));
    }

    AuditEntryId closed(RoleId roleId, RoleVersionId versionId, int versionNumber, UserId closedBy, Instant effectiveTo, Instant clockReading) {
        return audit.record(new AuditEntry("platform.identity.role-version.closed", closedBy, null, AuditSubject.of(AuditSubjectKind.ROLE, roleId.value()),
                "roleVersionId=" + versionId.value() + " versionNumber=" + versionNumber + " effectiveTo=" + effectiveTo + " clock=" + clockReading));
    }
}
