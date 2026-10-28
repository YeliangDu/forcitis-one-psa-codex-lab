package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.RoleChange;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The audit entries of role versions, written in the transaction that opens or closes the version so that the version row can carry the
 * entry's id (ADR-0034 decision 1 item 2; ADR-0034-IMPL ruling A-6). The detail holds ids, enum names and the clock reading only (card C4):
 * the version's effective time may be later than the clock (greatest(:now, ...)), so the actual reading is kept here.
 */
@Component
class RoleVersionAudit {

    /** An audit text holds at most 2000 characters; the units of a large role are counted past this length, not listed. */
    private static final int UNIT_TEXT_LIMIT = 1900;

    private final AuditLog audit;

    RoleVersionAudit(AuditLog audit) {
        this.audit = audit;
    }

    /**
     * When the custom org units differ from the replaced version's (F-1 card C4, "范围授予变更"), the entry also carries the units before and
     * after (ids and the "with descendants" choice only) and the change's fixed reason; otherwise it carries neither.
     */
    AuditEntryId opened(RoleId roleId, RoleVersionId versionId, int versionNumber, RoleChange change, Instant effectiveFrom, Instant clockReading,
                        Set<CustomOrgUnit> unitsBefore, Set<CustomOrgUnit> unitsAfter) {
        Integer declarationRevisionNumber = change.declarationRevisionNumber();
        AuditEntry entry = new AuditEntry("platform.identity.role-version.opened", change.recordedBy(), null, AuditSubject.of(AuditSubjectKind.ROLE, roleId.value()),
                "roleVersionId=" + versionId.value() + " versionNumber=" + versionNumber + " channel=" + change.channel().name()
                        + " declarationRevisionNumber=" + (declarationRevisionNumber == null ? "none" : declarationRevisionNumber)
                        + " effectiveFrom=" + effectiveFrom + " clock=" + clockReading);
        if (!unitsBefore.equals(unitsAfter)) {
            entry = entry.withChange(change.reason(), "customOrgUnits=" + CustomOrgUnit.auditText(unitsBefore, UNIT_TEXT_LIMIT),
                    "customOrgUnits=" + CustomOrgUnit.auditText(unitsAfter, UNIT_TEXT_LIMIT));
        }
        return audit.record(entry);
    }

    AuditEntryId closed(RoleId roleId, RoleVersionId versionId, int versionNumber, UserId closedBy, Instant effectiveTo, Instant clockReading) {
        return audit.record(new AuditEntry("platform.identity.role-version.closed", closedBy, null, AuditSubject.of(AuditSubjectKind.ROLE, roleId.value()),
                "roleVersionId=" + versionId.value() + " versionNumber=" + versionNumber + " effectiveTo=" + effectiveTo + " clock=" + clockReading));
    }
}
