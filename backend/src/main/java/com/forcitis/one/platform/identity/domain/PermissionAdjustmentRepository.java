package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;

public interface PermissionAdjustmentRepository {

    /** Whether the person already has an item of the kind for this trigger (one per triggering command and field; a redelivery adds none). */
    boolean existsForTrigger(PermissionAdjustmentKind kind, UserId user, PersonnelCommandId command, TriggerFieldKind field);

    void insert(PermissionAdjustment opened);
}
