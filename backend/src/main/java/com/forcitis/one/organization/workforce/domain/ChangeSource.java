package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import org.jspecify.annotations.Nullable;

/** Where a version row came from: the personnel command that wrote it with the audit entry of that write, or the migration (W2 design section 2). */
public record ChangeSource(boolean command, @Nullable PersonnelCommandId commandId, @Nullable AuditEntryId auditEntryId) {

    public static final ChangeSource MIGRATION = new ChangeSource(false, null, null);

    public static ChangeSource ofCommand(PersonnelCommandId commandId, AuditEntryId auditEntryId) {
        return new ChangeSource(true, commandId, auditEntryId);
    }

    public boolean isCommand(PersonnelCommandId id) {
        return command && id.equals(commandId);
    }
}
