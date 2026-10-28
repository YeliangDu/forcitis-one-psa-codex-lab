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
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The audit entries of role versions, written in the transaction that opens or closes the version so that the version row can carry the
 * entry's id (ADR-0034 decision 1 item 2; ADR-0034-IMPL ruling A-6). The detail holds ids, enum names and the clock reading only (card C4):
 * the version's effective time may be later than the clock (greatest(:now, ...)), so the actual reading is kept here.
 */
@Component
class RoleVersionAudit {

    /**
     * An audit text holds at most 2000 characters. The opened detail spends about 250 characters on ids and clock readings and about 200 on
     * keys; each side of a change lists at most this many characters of org unit ids, the rest is counted under "...NotListed", not listed.
     */
    private static final int IDS_LIMIT = 550;
    /** The same for the ids of the units chosen with descendants, a subset of the side's units. */
    private static final int WITH_DESCENDANTS_LIMIT = 150;

    private final AuditLog audit;

    RoleVersionAudit(AuditLog audit) {
        this.audit = audit;
    }

    /**
     * The detail is a JSON object. When the custom org units differ from the replaced version's (F-1 card C4, "范围授予变更"), it also carries
     * "before" and "after" (the org unit ids, strings) and "beforeWithDescendants" and "afterWithDescendants" (the ids chosen with
     * descendants); key names as the F-1 #728 acceptance notes fix them. before_text and after_text stay empty (DATA-REPAIR-timesheet-audit
     * row 19).
     */
    AuditEntryId opened(RoleId roleId, RoleVersionId versionId, int versionNumber, RoleChange change, Instant effectiveFrom, Instant clockReading,
                        Set<CustomOrgUnit> unitsBefore, Set<CustomOrgUnit> unitsAfter) {
        Integer declarationRevisionNumber = change.declarationRevisionNumber();
        StringBuilder json = new StringBuilder("{\"roleVersionId\":\"").append(versionId.value())
                .append("\",\"versionNumber\":").append(versionNumber)
                .append(",\"channel\":\"").append(change.channel().name())
                .append("\",\"declarationRevisionNumber\":").append(declarationRevisionNumber == null ? "null" : declarationRevisionNumber)
                .append(",\"effectiveFrom\":\"").append(effectiveFrom)
                .append("\",\"clock\":\"").append(clockReading).append('"');
        if (!unitsBefore.equals(unitsAfter)) {
            appendUnits(json, "before", unitsBefore);
            appendUnits(json, "after", unitsAfter);
        }
        return audit.record(new AuditEntry("platform.identity.role-version.opened", change.recordedBy(), null, AuditSubject.of(AuditSubjectKind.ROLE, roleId.value()),
                json.append('}').toString()));
    }

    AuditEntryId closed(RoleId roleId, RoleVersionId versionId, int versionNumber, UserId closedBy, Instant effectiveTo, Instant clockReading) {
        return audit.record(new AuditEntry("platform.identity.role-version.closed", closedBy, null, AuditSubject.of(AuditSubjectKind.ROLE, roleId.value()),
                "roleVersionId=" + versionId.value() + " versionNumber=" + versionNumber + " effectiveTo=" + effectiveTo + " clock=" + clockReading));
    }

    private static void appendUnits(StringBuilder json, String key, Set<CustomOrgUnit> units) {
        List<CustomOrgUnit> ordered = units.stream().sorted(CustomOrgUnit.BY_ORG_UNIT).toList();
        appendIds(json, key, ordered.stream().map(unit -> unit.orgUnitId().value().toString()).toList(), IDS_LIMIT);
        appendIds(json, key + "WithDescendants",
                ordered.stream().filter(CustomOrgUnit::includeDescendants).map(unit -> unit.orgUnitId().value().toString()).toList(), WITH_DESCENDANTS_LIMIT);
    }

    /** The ids are UUIDs, so they need no escaping; past {@code limit} characters the rest is counted under key + "NotListed". */
    private static void appendIds(StringBuilder json, String key, List<String> ids, int limit) {
        json.append(",\"").append(key).append("\":[");
        int listed = 0;
        int length = 0;
        for (String id : ids) {
            int one = id.length() + 3;
            if (length + one > limit) {
                break;
            }
            json.append(listed == 0 ? "" : ",").append('"').append(id).append('"');
            length += one;
            listed++;
        }
        json.append(']');
        if (listed < ids.size()) {
            json.append(",\"").append(key).append("NotListed\":").append(ids.size() - listed);
        }
    }
}
