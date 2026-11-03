package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import org.jspecify.annotations.Nullable;

/** Where a version row came from: the personnel or org-structure command that wrote it with the audit entry of that write, or the migration (W2 design section 2). */
public record ChangeSource(boolean command, @Nullable PersonnelCommandId commandId, @Nullable OrgStructureCommandId orgCommandId,
                           @Nullable AuditEntryId auditEntryId) {

    public static final ChangeSource MIGRATION = new ChangeSource(false, null, null, null);

    public static ChangeSource ofCommand(PersonnelCommandId commandId, AuditEntryId auditEntryId) {
        return new ChangeSource(true, commandId, null, auditEntryId);
    }

    /** The source of a row an org-structure command wrote (org unit, position and unit attribution rows). */
    public static ChangeSource ofOrgCommand(OrgStructureCommandId commandId, AuditEntryId auditEntryId) {
        return new ChangeSource(true, null, commandId, auditEntryId);
    }

    public boolean isCommand(PersonnelCommandId id) {
        return command && id.equals(commandId);
    }

    public boolean isOrgCommand(OrgStructureCommandId id) {
        return command && id.equals(orgCommandId);
    }
}
