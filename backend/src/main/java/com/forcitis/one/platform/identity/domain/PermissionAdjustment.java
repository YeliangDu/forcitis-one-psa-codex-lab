package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentAdjusted;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentEscalated;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentExpired;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentOpened;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentUpheld;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A review item that reminds the people entitled to decide that someone's authorizations should be looked at again (ADR-0032 decisions 4 and 5);
 * it never grants or takes back anything. It is raised OPEN and then upheld or marked adjusted, once; an item that is no longer OPEN takes no
 * further command. The daily jobs expire an upheld sensitive-role review and escalate an overdue open one; items of the other origins and the later states are written by the listeners that own them and are only read here.
 */
public final class PermissionAdjustment extends AggregateRoot {

    private final PermissionAdjustmentId id;
    private final PermissionAdjustmentKind kind;
    private final PermissionAdjustmentOrigin origin;
    private PermissionAdjustmentStatus status;
    private final @Nullable UserId user;
    private final @Nullable RoleId role;
    private final @Nullable OrgUnitId inactiveOrgUnit;
    private final @Nullable PersonnelCommandId triggerCommand;
    private final @Nullable TriggerFieldKind triggerField;
    private final @Nullable OrgUnitId beforeOrgUnit;
    private final @Nullable OrgUnitId afterOrgUnit;
    private final @Nullable PositionId beforePosition;
    private final @Nullable PositionId afterPosition;
    private final @Nullable EmployeeId recipient;
    private final Instant escalationDueAt;
    private final Instant createdAt;
    private @Nullable UserId upheldBy;
    private @Nullable UpholdReason upholdReason;
    private @Nullable EmployeeId businessOwner;
    private @Nullable Instant upholdExpiresAt;
    private @Nullable UserId adjustedBy;
    private @Nullable AdjustmentReference adjustedWith;
    private final @Nullable PermissionAdjustmentId original;
    private @Nullable Instant escalatedAt;
    private @Nullable Instant expiredAt;
    private @Nullable Boolean heldAtExpiry;
    private @Nullable Instant regenerationDecidedAt;

    private PermissionAdjustment(PermissionAdjustmentId id, PermissionAdjustmentKind kind, PermissionAdjustmentOrigin origin, UserId user,
            @Nullable PersonnelCommandId triggerCommand, @Nullable TriggerFieldKind triggerField, @Nullable OrgUnitId beforeOrgUnit,
            @Nullable OrgUnitId afterOrgUnit, @Nullable PositionId beforePosition, @Nullable PositionId afterPosition, @Nullable EmployeeId recipient,
            @Nullable PermissionAdjustmentId original, Instant escalationDueAt, Instant createdAt) {
        super(0, 0);
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.origin = origin;
        this.status = PermissionAdjustmentStatus.OPEN;
        this.user = Objects.requireNonNull(user, "user");
        this.role = null;
        this.inactiveOrgUnit = null;
        this.triggerCommand = triggerCommand;
        this.triggerField = triggerField;
        this.beforeOrgUnit = beforeOrgUnit;
        this.afterOrgUnit = afterOrgUnit;
        this.original = original;
        this.beforePosition = beforePosition;
        this.afterPosition = afterPosition;
        this.recipient = recipient;
        this.escalationDueAt = Objects.requireNonNull(escalationDueAt, "escalationDueAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private PermissionAdjustment(Stored stored) {
        super(stored.version(), stored.eventSequence());
        this.id = stored.id();
        this.kind = stored.kind();
        this.origin = stored.origin();
        this.status = stored.status();
        this.user = stored.user();
        this.role = stored.role();
        this.inactiveOrgUnit = stored.inactiveOrgUnit();
        this.triggerCommand = stored.triggerCommand();
        this.triggerField = stored.triggerField();
        this.beforeOrgUnit = stored.beforeOrgUnit();
        this.afterOrgUnit = stored.afterOrgUnit();
        this.beforePosition = stored.beforePosition();
        this.afterPosition = stored.afterPosition();
        this.recipient = stored.recipient();
        this.escalationDueAt = stored.escalationDueAt();
        this.createdAt = stored.createdAt();
        this.upheldBy = stored.upheldBy();
        this.businessOwner = stored.businessOwner();
        this.upholdExpiresAt = stored.upholdExpiresAt();
        this.original = stored.original();
        this.escalatedAt = stored.escalatedAt();
        this.expiredAt = stored.expiredAt();
        this.heldAtExpiry = stored.heldAtExpiry();
        this.regenerationDecidedAt = stored.regenerationDecidedAt();
    }

    /** An item as the table holds it, whichever origin raised it. */
    public record Stored(PermissionAdjustmentId id, PermissionAdjustmentKind kind, PermissionAdjustmentOrigin origin, PermissionAdjustmentStatus status,
                         @Nullable UserId user, @Nullable RoleId role, @Nullable OrgUnitId inactiveOrgUnit, @Nullable PersonnelCommandId triggerCommand,
                         @Nullable TriggerFieldKind triggerField, @Nullable OrgUnitId beforeOrgUnit, @Nullable OrgUnitId afterOrgUnit,
                         @Nullable PositionId beforePosition, @Nullable PositionId afterPosition, @Nullable EmployeeId recipient, Instant escalationDueAt,
                         Instant createdAt, @Nullable UserId upheldBy, @Nullable EmployeeId businessOwner, @Nullable Instant upholdExpiresAt,
                         @Nullable PermissionAdjustmentId original, @Nullable Instant escalatedAt, @Nullable Instant expiredAt, @Nullable Boolean heldAtExpiry,
                         @Nullable Instant regenerationDecidedAt, long version, long eventSequence) {
    }

    public static PermissionAdjustment restore(Stored stored) {
        return new PermissionAdjustment(stored);
    }

    /**
     * The handler upholds an OPEN item: the authorizations stay. A sensitive-role review names a business owner (an employee, given by the caller after
     * checking they are employed and not the person the item is about) and expires at the instant the use case worked out (the next natural quarter start, UpholdExpiry); the other kinds take neither.
     */
    public void uphold(UserId by, UpholdReason reason, @Nullable EmployeeId owner, Instant sensitiveExpiry) {
        requireOpen();
        requireNotAbout(by);
        boolean sensitive = kind == PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW;
        if (sensitive != (owner != null)) {
            throw new IllegalArgumentException("only a sensitive-role review is upheld with a business owner");
        }
        this.status = PermissionAdjustmentStatus.UPHELD;
        this.upheldBy = by;
        this.upholdReason = reason;
        this.businessOwner = owner;
        this.upholdExpiresAt = sensitive ? sensitiveExpiry : null;
        EmployeeId recordedOwner = owner;
        Instant recordedExpiry = upholdExpiresAt;
        record((envelopes, version) -> new PermissionAdjustmentUpheld(envelopes.wrap(draft("PermissionAdjustmentUpheld", version)), id, by, kind,
                recordedOwner, recordedExpiry, new Reason(reason.value())));
    }

    /** The handler shows an OPEN item was adjusted, by an authorization request or by a grant in the audit trail; nothing is granted or taken back here. */
    public void markAdjusted(UserId by, AdjustmentReference reference) {
        requireOpen();
        requireNotAbout(by);
        this.status = PermissionAdjustmentStatus.ADJUSTED;
        this.adjustedBy = by;
        this.adjustedWith = reference;
        record((envelopes, version) -> new PermissionAdjustmentAdjusted(envelopes.wrap(draft("PermissionAdjustmentAdjusted", version)), id, by, kind,
                reference.authorizationRequest(), reference.auditEntry()));
    }

    /**
     * The daily job finds an UPHELD sensitive-role review at or past its expiry (ADR-0044 decision 2 item 3): it turns EXPIRED at the job's clock,
     * records whether the person was held that day, and, unless held, is decided at once whether a new review follows. Nothing is granted or taken back.
     */
    public void expire(Instant now, boolean held) {
        if (status != PermissionAdjustmentStatus.UPHELD || upholdExpiresAt == null || now.isBefore(upholdExpiresAt)) {
            throw new IllegalStateException("the item " + id.value() + " is not an upheld item at its expiry");
        }
        this.status = PermissionAdjustmentStatus.EXPIRED;
        this.expiredAt = now;
        this.heldAtExpiry = held;
        this.regenerationDecidedAt = held ? null : now;
        Instant expiry = upholdExpiresAt;
        record((envelopes, version) -> new PermissionAdjustmentExpired(envelopes.wrap(draft("PermissionAdjustmentExpired", version)), id, kind, expiry, now,
                held));
    }

    /** The job found an EXPIRED item that waited for the release of a held separation, and has now decided about its successor (invariant 26). */
    public void decideRegeneration(Instant now) {
        if (status != PermissionAdjustmentStatus.EXPIRED || regenerationDecidedAt != null) {
            throw new IllegalStateException("the item " + id.value() + " has no pending regeneration decision");
        }
        this.regenerationDecidedAt = now;
    }

    /** The job finds an OPEN item past its escalation due time that was not escalated yet: it is marked once, stays OPEN and nothing is taken back (invariant 30). */
    public void escalate(Instant now) {
        if (status != PermissionAdjustmentStatus.OPEN || escalatedAt != null || now.isBefore(escalationDueAt)) {
            throw new IllegalStateException("the item " + id.value() + " is not an open, unescalated item past its due time");
        }
        this.escalatedAt = now;
        record((envelopes, version) -> new PermissionAdjustmentEscalated(envelopes.wrap(draft("PermissionAdjustmentEscalated", version)), id, kind,
                escalationDueAt));
    }

    private void requireOpen() {
        if (status != PermissionAdjustmentStatus.OPEN) {
            throw IdentityError.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_NOT_OPEN.refusal("the item " + id.value() + " is " + status + " and takes no further command");
        }
    }

    private void requireNotAbout(UserId by) {
        if (by.equals(user)) {
            throw IdentityError.PLATFORM_IDENTITY_OWN_PERMISSION_ADJUSTMENT_FORBIDDEN.refusal("the item " + id.value() + " is about the handler");
        }
    }

    /**
     * One sensitive-role review for a person whose assignment changed in one field (ADR-0032 decision 5). It names no role; the reviewer sees what
     * the person holds today. The department before is empty only when a wrong separation was undone (ADR-0082 decision 2), and then so is the position.
     */
    public static PermissionAdjustment assignmentChangeReview(PermissionAdjustmentId id, UserId user, PersonnelCommandId command, TriggerFieldKind field,
            @Nullable OrgUnitId beforeOrgUnit, OrgUnitId afterOrgUnit, @Nullable PositionId beforePosition, @Nullable PositionId afterPosition,
            Instant escalationDueAt, Instant at) {
        return opened(PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW, id, user, command, field, beforeOrgUnit, afterOrgUnit, beforePosition, afterPosition,
                null, escalationDueAt, at);
    }

    /**
     * One scope review for a person whose department changed, addressed to the head of the new department, or to nobody when it has none
     * (ADR-0032 decision 4, N1-09). It names no role.
     */
    public static PermissionAdjustment departmentChangeScopeReview(PermissionAdjustmentId id, UserId user, PersonnelCommandId command, OrgUnitId beforeOrgUnit,
            OrgUnitId afterOrgUnit, @Nullable PositionId beforePosition, @Nullable PositionId afterPosition, @Nullable EmployeeId recipient,
            Instant escalationDueAt, Instant at) {
        return opened(PermissionAdjustmentKind.SCOPE_REVIEW, id, user, command, TriggerFieldKind.ORG_UNIT, beforeOrgUnit, afterOrgUnit, beforePosition,
                afterPosition, recipient, escalationDueAt, at);
    }

    /**
     * The successor of an item whose upholding expired, for the same person and the same kind of review (ADR-0044 decision 2 item 3); it names the
     * expired item and no trigger, and has its own escalation due date.
     */
    public static PermissionAdjustment expiryReview(PermissionAdjustmentId id, PermissionAdjustment expired, Instant escalationDueAt, Instant at) {
        UserId person = Objects.requireNonNull(expired.user, "user");
        return opened(PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW, PermissionAdjustmentOrigin.UPHOLD_EXPIRY, id, person, null, null, null, null, null,
                null, null, expired.id, escalationDueAt, at);
    }

    private static PermissionAdjustment opened(PermissionAdjustmentKind kind, PermissionAdjustmentId id, UserId user, PersonnelCommandId command,
            TriggerFieldKind field, @Nullable OrgUnitId beforeOrgUnit, OrgUnitId afterOrgUnit, @Nullable PositionId beforePosition,
            @Nullable PositionId afterPosition, @Nullable EmployeeId recipient, Instant escalationDueAt, Instant at) {
        return opened(kind, PermissionAdjustmentOrigin.ASSIGNMENT_CHANGE, id, user, command, field, beforeOrgUnit, afterOrgUnit, beforePosition,
                afterPosition, recipient, null, escalationDueAt, at);
    }

    private static PermissionAdjustment opened(PermissionAdjustmentKind kind, PermissionAdjustmentOrigin origin, PermissionAdjustmentId id, UserId user,
            @Nullable PersonnelCommandId command, @Nullable TriggerFieldKind field, @Nullable OrgUnitId beforeOrgUnit, @Nullable OrgUnitId afterOrgUnit,
            @Nullable PositionId beforePosition, @Nullable PositionId afterPosition, @Nullable EmployeeId recipient, @Nullable PermissionAdjustmentId original,
            Instant escalationDueAt, Instant at) {
        PermissionAdjustment item = new PermissionAdjustment(id, kind, origin, user, command, field, beforeOrgUnit, afterOrgUnit, beforePosition,
                afterPosition, recipient, original, escalationDueAt, at);
        item.record((envelopes, version) -> new PermissionAdjustmentOpened(envelopes.wrap(item.draft("PermissionAdjustmentOpened", version)),
                id, user, item.kind, item.origin, command, field, beforeOrgUnit, afterOrgUnit, beforePosition, afterPosition, original, escalationDueAt));
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

    public @Nullable UserId user() {
        return user;
    }

    public @Nullable RoleId role() {
        return role;
    }

    public @Nullable OrgUnitId inactiveOrgUnit() {
        return inactiveOrgUnit;
    }

    public @Nullable PersonnelCommandId triggerCommand() {
        return triggerCommand;
    }

    public @Nullable TriggerFieldKind triggerField() {
        return triggerField;
    }

    public @Nullable UserId upheldBy() {
        return upheldBy;
    }

    public @Nullable UpholdReason upholdReason() {
        return upholdReason;
    }

    public @Nullable EmployeeId businessOwner() {
        return businessOwner;
    }

    public @Nullable Instant upholdExpiresAt() {
        return upholdExpiresAt;
    }

    public @Nullable UserId adjustedBy() {
        return adjustedBy;
    }

    public @Nullable AdjustmentReference adjustedWith() {
        return adjustedWith;
    }

    public @Nullable OrgUnitId beforeOrgUnit() {
        return beforeOrgUnit;
    }

    public @Nullable OrgUnitId afterOrgUnit() {
        return afterOrgUnit;
    }

    public @Nullable PositionId beforePosition() {
        return beforePosition;
    }

    public @Nullable PositionId afterPosition() {
        return afterPosition;
    }

    public @Nullable EmployeeId recipient() {
        return recipient;
    }

    public Instant escalationDueAt() {
        return escalationDueAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public @Nullable PermissionAdjustmentId original() {
        return original;
    }

    public @Nullable Instant escalatedAt() {
        return escalatedAt;
    }

    public @Nullable Instant expiredAt() {
        return expiredAt;
    }

    public @Nullable Boolean heldAtExpiry() {
        return heldAtExpiry;
    }

    public @Nullable Instant regenerationDecidedAt() {
        return regenerationDecidedAt;
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.identity." + eventName, 1, "PermissionAdjustment", id.value(), version, null, null, List.of());
    }
}
