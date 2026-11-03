package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.RegionKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An org-structure command (ADR-0068 item 4): what to do to one org unit or position from one business day on. The row is append-only; only the
 * status, the application time, the withdrawal columns and a merge's progress ever change. The content components are the ones the kind needs and only
 * those, the same shapes the table's CHECK constraints hold. Its events are about the command (one at most, so the sequence is 1): org units and
 * positions keep no event counter.
 */
public final class OrgStructureCommand extends AggregateRoot {
    public static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_NAME = 500;
    private static final int MAX_SHORT_NAME = 50;

    /** The content components of a command, by the name of the request component. */
    private enum Part {
        CODE, NAME, SHORT_NAME, REGION, DELIVERY, PARENT, HEAD, RECEIVABLES_MANAGER, MERGE_INTO, LEGAL_ENTITY
    }

    private final OrgStructureCommandId id;
    private final OrgStructureTargetKind targetKind;
    private final OrgStructureCommandKind kind;
    private final @Nullable OrgUnitId targetOrgUnitId;
    private final @Nullable PositionId targetPositionId;
    private final LocalDate effectiveDate;
    private final @Nullable String targetCode;
    private final @Nullable String targetName;
    private final @Nullable String targetShortName;
    private final @Nullable RegionKind targetRegionKind;
    private final @Nullable Boolean targetIsDeliveryUnit;
    private final @Nullable OrgUnitId targetParentOrgUnitId;
    private final @Nullable EmployeeId targetHeadEmployeeId;
    private final @Nullable EmployeeId targetReceivablesManagerEmployeeId;
    private final @Nullable OrgUnitId mergeIntoOrgUnitId;
    private final @Nullable LegalEntityId targetLegalEntityId;
    private final UserId createdBy;
    private final Instant createdAt;
    private OrgCommandStatus status;
    private @Nullable Integer affectedEmployeeCount;
    private @Nullable Integer transferredEmployeeCount;
    private @Nullable Instant appliedAt;
    private @Nullable Instant withdrawnAt;
    private @Nullable UserId withdrawnBy;
    private @Nullable String withdrawalReason;

    // The stored columns in table order: restore() is the one constructor, enter() builds the arguments.
    @SuppressWarnings("checkstyle:ParameterNumber")
    private OrgStructureCommand(OrgStructureCommandId id, OrgStructureTargetKind targetKind, OrgStructureCommandKind kind, @Nullable OrgUnitId targetOrgUnitId,
                                @Nullable PositionId targetPositionId, LocalDate effectiveDate, @Nullable String targetCode, @Nullable String targetName,
                                @Nullable String targetShortName, @Nullable RegionKind targetRegionKind, @Nullable Boolean targetIsDeliveryUnit,
                                @Nullable OrgUnitId targetParentOrgUnitId, @Nullable EmployeeId targetHeadEmployeeId,
                                @Nullable EmployeeId targetReceivablesManagerEmployeeId, @Nullable OrgUnitId mergeIntoOrgUnitId,
                                @Nullable LegalEntityId targetLegalEntityId, OrgCommandStatus status, @Nullable Integer affectedEmployeeCount,
                                @Nullable Integer transferredEmployeeCount, UserId createdBy, Instant createdAt, @Nullable Instant appliedAt, long version) {
        super(version, 0);
        this.id = Objects.requireNonNull(id, "id");
        this.targetKind = targetKind;
        this.kind = kind;
        this.targetOrgUnitId = targetOrgUnitId;
        this.targetPositionId = targetPositionId;
        this.effectiveDate = effectiveDate;
        this.targetCode = targetCode;
        this.targetName = targetName;
        this.targetShortName = targetShortName;
        this.targetRegionKind = targetRegionKind;
        this.targetIsDeliveryUnit = targetIsDeliveryUnit;
        this.targetParentOrgUnitId = targetParentOrgUnitId;
        this.targetHeadEmployeeId = targetHeadEmployeeId;
        this.targetReceivablesManagerEmployeeId = targetReceivablesManagerEmployeeId;
        this.mergeIntoOrgUnitId = mergeIntoOrgUnitId;
        this.targetLegalEntityId = targetLegalEntityId;
        this.status = status;
        this.affectedEmployeeCount = affectedEmployeeCount;
        this.transferredEmployeeCount = transferredEmployeeCount;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.appliedAt = appliedAt;
    }

    /**
     * A command about to be stored. A CREATE gets the id of the unit or position it makes from the server ({@code newTarget}); every other kind names its
     * target. A day that is not after today takes effect at once, a later one waits (ADR-0068 item 1.8); a merge always waits for the due-date step.
     */
    public static OrgStructureCommand enter(OrgStructureCommandId id, EnterOrgStructureCommand r, UUID newTarget, UserId createdBy, Instant now, LocalDate today) {
        OrgStructureCommandKind kind = require(r.commandKind(), "a command kind");
        OrgStructureTargetKind targetKind = require(r.targetKind(), "a target kind");
        LocalDate day = require(r.effectiveDate(), "the effective date");
        requireTarget(kind, targetKind, r);
        OrgUnitId unit = r.targetOrgUnitId();
        PositionId position = r.targetPositionId();
        if (kind == OrgStructureCommandKind.CREATE) {
            unit = targetKind == OrgStructureTargetKind.ORG_UNIT ? new OrgUnitId(newTarget) : null;
            position = targetKind == OrgStructureTargetKind.POSITION ? new PositionId(newTarget) : null;
        }
        OrgCommandStatus status = kind != OrgStructureCommandKind.MERGE && !day.isAfter(today) ? OrgCommandStatus.EFFECTIVE : OrgCommandStatus.PENDING;
        OrgStructureCommand command = new OrgStructureCommand(id, targetKind, kind, unit, position, day, r.targetCode(), r.targetName(), r.targetShortName(),
                r.targetRegionKind(), r.targetIsDeliveryUnit(), r.targetParentOrgUnitId(), r.targetHeadEmployeeId(), r.targetReceivablesManagerEmployeeId(),
                r.mergeIntoOrgUnitId(), r.targetLegalEntityId(), status, null, null, createdBy, now, status == OrgCommandStatus.EFFECTIVE ? now : null, 0);
        command.requireContent(r);
        return command;
    }

    /** A stored command. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static OrgStructureCommand restore(OrgStructureCommandId id, OrgStructureTargetKind targetKind, OrgStructureCommandKind kind, @Nullable OrgUnitId targetOrgUnitId,
                                              @Nullable PositionId targetPositionId, LocalDate effectiveDate, @Nullable String targetCode, @Nullable String targetName,
                                              @Nullable String targetShortName, @Nullable RegionKind targetRegionKind, @Nullable Boolean targetIsDeliveryUnit,
                                              @Nullable OrgUnitId targetParentOrgUnitId, @Nullable EmployeeId targetHeadEmployeeId,
                                              @Nullable EmployeeId targetReceivablesManagerEmployeeId, @Nullable OrgUnitId mergeIntoOrgUnitId,
                                              @Nullable LegalEntityId targetLegalEntityId, OrgCommandStatus status, @Nullable Integer affectedEmployeeCount,
                                              @Nullable Integer transferredEmployeeCount, UserId createdBy, Instant createdAt, @Nullable Instant appliedAt, long version) {
        return new OrgStructureCommand(id, targetKind, kind, targetOrgUnitId, targetPositionId, effectiveDate, targetCode, targetName, targetShortName, targetRegionKind,
                targetIsDeliveryUnit, targetParentOrgUnitId, targetHeadEmployeeId, targetReceivablesManagerEmployeeId, mergeIntoOrgUnitId, targetLegalEntityId, status,
                affectedEmployeeCount, transferredEmployeeCount, createdBy, createdAt, appliedAt, version);
    }

    private static void requireTarget(OrgStructureCommandKind kind, OrgStructureTargetKind targetKind, EnterOrgStructureCommand r) {
        boolean unit = targetKind == OrgStructureTargetKind.ORG_UNIT;
        boolean allowed = unit ? kind != OrgStructureCommandKind.DISABLE : kind == OrgStructureCommandKind.CREATE || kind == OrgStructureCommandKind.DISABLE;
        if (!allowed) {
            throw invalid("a " + targetKind + " takes no " + kind + " command");
        }
        if (kind == OrgStructureCommandKind.CREATE) {
            if (r.targetOrgUnitId() != null || r.targetPositionId() != null) {
                throw invalid("a CREATE makes its own target; it names none");
            }
        } else if (unit ? r.targetOrgUnitId() == null || r.targetPositionId() != null : r.targetPositionId() == null || r.targetOrgUnitId() != null) {
            throw invalid("a " + kind + " names the " + (unit ? "org unit" : "position") + " it is about, and only that");
        }
    }

    /** The content the kind needs and only that (ck_org_structure_commands_*_columns), and the length limits of the text. */
    private void requireContent(EnterOrgStructureCommand r) {
        Set<Part> given = EnumSet.noneOf(Part.class);
        add(given, Part.CODE, r.targetCode());
        add(given, Part.NAME, r.targetName());
        add(given, Part.SHORT_NAME, r.targetShortName());
        add(given, Part.REGION, r.targetRegionKind());
        add(given, Part.DELIVERY, r.targetIsDeliveryUnit());
        add(given, Part.PARENT, r.targetParentOrgUnitId());
        add(given, Part.HEAD, r.targetHeadEmployeeId());
        add(given, Part.RECEIVABLES_MANAGER, r.targetReceivablesManagerEmployeeId());
        add(given, Part.MERGE_INTO, r.mergeIntoOrgUnitId());
        add(given, Part.LEGAL_ENTITY, r.targetLegalEntityId());
        boolean unit = targetKind == OrgStructureTargetKind.ORG_UNIT;
        Set<Part> required = EnumSet.noneOf(Part.class);
        Set<Part> optional = EnumSet.noneOf(Part.class);
        switch (kind) {
            case CREATE -> {
                required.add(Part.NAME);
                optional.add(Part.CODE);
                if (unit) {
                    required.add(Part.DELIVERY);
                    optional.addAll(Set.of(Part.SHORT_NAME, Part.REGION, Part.PARENT, Part.LEGAL_ENTITY));
                }
            }
            case SET_ATTRIBUTES -> optional.addAll(Set.of(Part.NAME, Part.SHORT_NAME, Part.REGION, Part.DELIVERY));
            case REPARENT -> required.add(Part.PARENT);
            case ASSIGN_HEAD -> required.add(Part.HEAD);
            case ASSIGN_RECEIVABLES_MANAGER -> required.add(Part.RECEIVABLES_MANAGER);
            case MERGE -> required.add(Part.MERGE_INTO);
            case DISSOLVE, DISABLE -> {
            }
        }
        if (!given.containsAll(required)) {
            throw invalid("a " + kind + " needs " + required);
        }
        Set<Part> extra = EnumSet.noneOf(Part.class);
        extra.addAll(given);
        extra.removeAll(required);
        extra.removeAll(optional);
        if (!extra.isEmpty()) {
            throw invalid("a " + kind + " carries no " + extra);
        }
        if (kind == OrgStructureCommandKind.SET_ATTRIBUTES && given.isEmpty()) {
            throw invalid("a SET_ATTRIBUTES changes at least one attribute");
        }
        requireText(targetName, MAX_NAME, "the name");
        requireText(targetShortName, MAX_SHORT_NAME, "the short name");
        requireText(targetCode, MAX_NAME, "the code");
        if (mergeIntoOrgUnitId != null && mergeIntoOrgUnitId.equals(targetOrgUnitId) || targetParentOrgUnitId != null && targetParentOrgUnitId.equals(targetOrgUnitId)) {
            throw invalid("an org unit is not its own " + (mergeIntoOrgUnitId != null ? "merge target" : "parent"));
        }
    }

    private static void add(Set<Part> given, Part part, @Nullable Object value) {
        if (value != null) {
            given.add(part);
        }
    }

    private static void requireText(@Nullable String text, int max, String what) {
        if (text != null && (text.isBlank() || text.length() > max)) {
            throw invalid(what + " is 1 to " + max + " characters and not blank");
        }
    }

    /** Pending to withdrawn; a merge that has started cannot be taken back (the status machine refuses). */
    public void withdraw(UserId by, @Nullable String reason, Instant now) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_REASON_INVALID, ErrorKind.UNPROCESSABLE,
                    "the reason is 1 to " + MAX_REASON_LENGTH + " characters and not blank");
        }
        moveTo(OrgCommandStatus.WITHDRAWN);
        this.withdrawnAt = now;
        this.withdrawnBy = by;
        this.withdrawalReason = reason;
    }

    /** A merge starts moving people: the number of employed people of that moment is written once. */
    public void startMerge(int affected) {
        requireKind(OrgStructureCommandKind.MERGE);
        moveTo(OrgCommandStatus.IN_PROGRESS);
        this.affectedEmployeeCount = affected;
        this.transferredEmployeeCount = 0;
    }

    /** One more person is moved. */
    public void recordTransferred() {
        Integer affected = affectedEmployeeCount;
        Integer done = transferredEmployeeCount;
        if (status != OrgCommandStatus.IN_PROGRESS || affected == null || done == null || done >= affected) {
            throw notAllowed("no person is left to move");
        }
        transferredEmployeeCount = done + 1;
    }

    /**
     * Nobody is left in the merged unit, so everyone the merge counted is out of it. A person who was refused and then moved out by another effective command
     * counts as done (design note section 8, user answer QF1 a); only the check "nobody is left" lets this be called.
     */
    public void countRemainingAsMovedOut() {
        requireKind(OrgStructureCommandKind.MERGE);
        if (status != OrgCommandStatus.IN_PROGRESS) {
            throw notAllowed("the merge is not in progress");
        }
        transferredEmployeeCount = affectedEmployeeCount;
    }

    /** A merge is effective only when everyone it counted has been moved (ck_org_structure_commands_merge_progress). */
    public void completeMerge(Instant now) {
        requireKind(OrgStructureCommandKind.MERGE);
        if (!Objects.equals(affectedEmployeeCount, transferredEmployeeCount)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_MERGE_INCOMPLETE, ErrorKind.CONFLICT, "somebody is not moved yet");
        }
        moveTo(OrgCommandStatus.EFFECTIVE);
        this.appliedAt = now;
    }

    private void requireKind(OrgStructureCommandKind expected) {
        if (kind != expected) {
            throw notAllowed("only a " + expected + " does this");
        }
    }

    private void moveTo(OrgCommandStatus next) {
        if (!OrgCommandStatus.MACHINE.allows(status, next)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT,
                    "the command is " + status + " and cannot become " + next);
        }
        this.status = next;
    }

    /** Whether the events of this kind are published when it was entered (everything but a merge, which publishes when the last person is moved). */
    public boolean takesEffectAtEntry() {
        return kind != OrgStructureCommandKind.MERGE && status == OrgCommandStatus.EFFECTIVE;
    }

    /** Records the event of the kind; call once, when the command has taken effect. */
    public void recordEffect() {
        OrgCommandEvents.of(this).ifPresent(this::record);
    }

    private static <T> T require(@Nullable T value, String what) {
        if (value == null) {
            throw invalid(what + " is required");
        }
        return value;
    }

    private DomainException notAllowed(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT, message);
    }

    private static DomainException invalid(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, message);
    }

    public OrgStructureCommandId id() {
        return id;
    }

    public OrgStructureTargetKind targetKind() {
        return targetKind;
    }

    public OrgStructureCommandKind kind() {
        return kind;
    }

    public @Nullable OrgUnitId targetOrgUnitId() {
        return targetOrgUnitId;
    }

    public @Nullable PositionId targetPositionId() {
        return targetPositionId;
    }

    public LocalDate effectiveDate() {
        return effectiveDate;
    }

    public @Nullable String targetCode() {
        return targetCode;
    }

    public @Nullable String targetName() {
        return targetName;
    }

    public @Nullable String targetShortName() {
        return targetShortName;
    }

    public @Nullable RegionKind targetRegionKind() {
        return targetRegionKind;
    }

    public @Nullable Boolean targetIsDeliveryUnit() {
        return targetIsDeliveryUnit;
    }

    public @Nullable OrgUnitId targetParentOrgUnitId() {
        return targetParentOrgUnitId;
    }

    public @Nullable EmployeeId targetHeadEmployeeId() {
        return targetHeadEmployeeId;
    }

    public @Nullable EmployeeId targetReceivablesManagerEmployeeId() {
        return targetReceivablesManagerEmployeeId;
    }

    public @Nullable OrgUnitId mergeIntoOrgUnitId() {
        return mergeIntoOrgUnitId;
    }

    public @Nullable LegalEntityId targetLegalEntityId() {
        return targetLegalEntityId;
    }

    public OrgCommandStatus status() {
        return status;
    }

    public @Nullable Integer affectedEmployeeCount() {
        return affectedEmployeeCount;
    }

    public @Nullable Integer transferredEmployeeCount() {
        return transferredEmployeeCount;
    }

    public UserId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public @Nullable Instant appliedAt() {
        return appliedAt;
    }

    public @Nullable Instant withdrawnAt() {
        return withdrawnAt;
    }

    public @Nullable UserId withdrawnBy() {
        return withdrawnBy;
    }

    public @Nullable String withdrawalReason() {
        return withdrawalReason;
    }
}
