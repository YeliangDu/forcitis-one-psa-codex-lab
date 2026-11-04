package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.RoleId;
import java.time.Instant;
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

    /** At most limit UPHELD items whose expiry is at or before now and whose id sorts after the given one, in creation order (a cursor; null starts). */
    List<PermissionAdjustmentId> dueToExpire(Instant now, @Nullable PermissionAdjustmentId after, int limit);

    /** At most limit EXPIRED items that were held when they expired and wait for the decision about their successor and whose id sorts after the given one, in creation order. */
    /** The person's OPEN items, oldest first. */
    List<PermissionAdjustmentId> openOf(UserId user);

    /** The OPEN items of the role that are about a dissolved or merged-away org unit, oldest first. */
    List<PermissionAdjustmentId> openAboutInactiveUnitsOf(RoleId role);

    /** Whether the role already has an OPEN or UPHELD item about the org unit (the table allows one at a time). */
    boolean existsOpenOrUpheldForInactiveUnit(RoleId role, OrgUnitId orgUnit);

    List<PermissionAdjustmentId> pendingRegeneration(@Nullable PermissionAdjustmentId after, int limit);

    /** At most limit OPEN items not escalated yet whose escalation due time is at or before now and whose id sorts after the given one, in creation order. */
    List<PermissionAdjustmentId> overdueOpen(Instant now, @Nullable PermissionAdjustmentId after, int limit);

    /**
     * The items the reader may read, newest first, at most limit of them after the item the cursor names (null: from the start), optionally of one
     * status. The read rule of {@link PermissionAdjustmentReader#canRead} is applied here, in the query.
     */
    List<PermissionAdjustment> findFor(PermissionAdjustmentReader reader, @Nullable PermissionAdjustmentStatus status, @Nullable PermissionAdjustmentKind kind,
            @Nullable PermissionAdjustmentId after, int limit);
}
