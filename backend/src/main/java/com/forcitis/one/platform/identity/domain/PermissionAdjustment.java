package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentOpened;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A review item that reminds the people entitled to decide that someone's authorizations should be looked at again (ADR-0032 decisions 4 and 5);
 * it never grants or takes back anything. Here it is only raised; the decisions that move it on are added with their use cases.
 */
public final class PermissionAdjustment extends AggregateRoot {

    private final PermissionAdjustmentId id;
    private final PermissionAdjustmentKind kind;
    private final PermissionAdjustmentOrigin origin;
    private final PermissionAdjustmentStatus status;
    private final UserId user;
    private final PersonnelCommandId triggerCommand;
    private final TriggerFieldKind triggerField;
    private final @Nullable OrgUnitId beforeOrgUnit;
    private final OrgUnitId afterOrgUnit;
    private final @Nullable PositionId beforePosition;
    private final @Nullable PositionId afterPosition;
    private final Instant escalationDueAt;
    private final Instant createdAt;

    private PermissionAdjustment(PermissionAdjustmentId id, PermissionAdjustmentKind kind, UserId user, PersonnelCommandId triggerCommand,
            TriggerFieldKind triggerField, @Nullable OrgUnitId beforeOrgUnit, OrgUnitId afterOrgUnit, @Nullable PositionId beforePosition,
            @Nullable PositionId afterPosition, Instant escalationDueAt, Instant createdAt) {
        super(0, 0);
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.origin = PermissionAdjustmentOrigin.ASSIGNMENT_CHANGE;
        this.status = PermissionAdjustmentStatus.OPEN;
        this.user = Objects.requireNonNull(user, "user");
        this.triggerCommand = Objects.requireNonNull(triggerCommand, "triggerCommand");
        this.triggerField = Objects.requireNonNull(triggerField, "triggerField");
        this.beforeOrgUnit = beforeOrgUnit;
        this.afterOrgUnit = Objects.requireNonNull(afterOrgUnit, "afterOrgUnit");
        this.beforePosition = beforePosition;
        this.afterPosition = afterPosition;
        this.escalationDueAt = Objects.requireNonNull(escalationDueAt, "escalationDueAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * One item for a person who holds sensitive roles and is employed again after a wrong separation was corrected: the department before is empty
     * and so is the position (ADR-0082 decision 2). It names no role; the reviewer sees what the person holds today.
     */
    public static PermissionAdjustment separationCorrectionReview(PermissionAdjustmentId id, UserId user, PersonnelCommandId command, OrgUnitId toOrgUnit,
            @Nullable PositionId toPosition, Instant escalationDueAt, Instant at) {
        PermissionAdjustment item = new PermissionAdjustment(id, PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW, user, command, TriggerFieldKind.ORG_UNIT,
                null, toOrgUnit, null, toPosition, escalationDueAt, at);
        item.record((envelopes, version) -> new PermissionAdjustmentOpened(envelopes.wrap(item.draft("PermissionAdjustmentOpened", version)),
                id, user));
        return item;
    }

    public PermissionAdjustmentId id() {
        return id;
    }

    public PermissionAdjustmentKind kind() {
        return kind;
    }

    public PermissionAdjustmentOrigin origin() {
        return origin;
    }

    public PermissionAdjustmentStatus status() {
        return status;
    }

    public UserId user() {
        return user;
    }

    public PersonnelCommandId triggerCommand() {
        return triggerCommand;
    }

    public TriggerFieldKind triggerField() {
        return triggerField;
    }

    public @Nullable OrgUnitId beforeOrgUnit() {
        return beforeOrgUnit;
    }

    public OrgUnitId afterOrgUnit() {
        return afterOrgUnit;
    }

    public @Nullable PositionId beforePosition() {
        return beforePosition;
    }

    public @Nullable PositionId afterPosition() {
        return afterPosition;
    }

    public Instant escalationDueAt() {
        return escalationDueAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.identity." + eventName, 1, "PermissionAdjustment", id.value(), version, null, null, List.of());
    }
}
