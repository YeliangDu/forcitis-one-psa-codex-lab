package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface PermissionAdjustmentRepository {

    /** Whether the person already has an item of the kind for this trigger (one per triggering command and field; a redelivery adds none). */
    boolean existsForTrigger(PermissionAdjustmentKind kind, UserId user, PersonnelCommandId command, TriggerFieldKind field);

    void insert(PermissionAdjustment opened);

    Optional<PermissionAdjustment> findById(PermissionAdjustmentId id);

    /** Writes the state a command changed; the version the item was read at must still be the stored one. */
    void update(PermissionAdjustment changed);

    /**
     * The items the reader may read, newest first, at most limit of them after the item the cursor names (null: from the start), optionally of one
     * status. The read rule of {@link PermissionAdjustmentReader#canRead} is applied here, in the query.
     */
    List<PermissionAdjustment> findFor(PermissionAdjustmentReader reader, @Nullable PermissionAdjustmentStatus status, @Nullable PermissionAdjustmentKind kind,
            @Nullable PermissionAdjustmentId after, int limit);
}
