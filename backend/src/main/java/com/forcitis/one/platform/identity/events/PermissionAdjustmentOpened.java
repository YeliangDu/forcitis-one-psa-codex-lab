package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A review item for the authorizations of one person was raised; it only reminds, nothing is granted or taken back. */
public record PermissionAdjustmentOpened(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, UserId userId, PermissionAdjustmentKind kind,
                                         PermissionAdjustmentOrigin originKind, PersonnelCommandId triggerPersonnelCommandId, TriggerFieldKind triggerFieldKind,
                                         @Nullable OrgUnitId beforeOrgUnitId, OrgUnitId afterOrgUnitId, Instant escalationDueAt) implements DomainEvent, AuditDetail {

    @Override
    public String auditDetail() {
        return "item " + permissionAdjustmentId.value() + " " + kind + " origin " + originKind + "; user " + userId.value()
                + "; trigger command " + triggerPersonnelCommandId.value() + " field " + triggerFieldKind
                + (beforeOrgUnitId == null ? "" : "; before org " + beforeOrgUnitId.value())
                + "; after org " + afterOrgUnitId.value() + "; escalation due " + escalationDueAt;
    }
}
