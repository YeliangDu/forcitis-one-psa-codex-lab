package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The hold a held separation carries (ADR-0068 item 2). It is written with the held command, in the same transaction, and moves from HELD to
 * RELEASED by a release or to WITHDRAWN with its command (item 1.6). The release columns are set once together (the table's checks and trigger).
 */
public final class SeparationHold {
    public static final int MAX_REASON_LENGTH = 500;

    private final SeparationHoldId id;
    private final PersonnelCommandId commandId;
    private final EmployeeId employeeId;
    private HoldStatus status;
    private long version;
    private @Nullable Instant releasedAt;
    private @Nullable UserId releasedBy;
    private @Nullable String releaseReason;
    private @Nullable ReleaseChannelKind releaseChannel;

    private SeparationHold(SeparationHoldId id, PersonnelCommandId commandId, EmployeeId employeeId, HoldStatus status, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.commandId = Objects.requireNonNull(commandId, "commandId");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    /** The hold of a command that was just held. */
    public static SeparationHold of(SeparationHoldId id, PersonnelCommand heldCommand) {
        return new SeparationHold(id, heldCommand.id(), heldCommand.employeeId(), HoldStatus.HELD, 0);
    }

    public static SeparationHold restore(SeparationHoldId id, PersonnelCommandId commandId, EmployeeId employeeId, HoldStatus status, long version,
                                         @Nullable Instant releasedAt, @Nullable UserId releasedBy, @Nullable String releaseReason,
                                         @Nullable ReleaseChannelKind releaseChannel) {
        SeparationHold hold = new SeparationHold(id, commandId, employeeId, status, version);
        hold.releasedAt = releasedAt;
        hold.releasedBy = releasedBy;
        hold.releaseReason = releaseReason;
        hold.releaseChannel = releaseChannel;
        return hold;
    }

    public void withdraw() {
        status = HoldStatus.MACHINE.require(status, HoldStatus.WITHDRAWN, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
        version = version + 1;
    }

    /** Releases a held hold in the interface; a hold that is already released or withdrawn is refused. */
    public void release(UserId by, @Nullable String reason, Instant at) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED, ErrorKind.INVALID,
                    "a release needs a reason of 1 to " + MAX_REASON_LENGTH + " characters that is not blank");
        }
        if (status != HoldStatus.HELD) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT,
                    "only a held separation can be released, this one is " + status);
        }
        status = HoldStatus.MACHINE.require(status, HoldStatus.RELEASED, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
        version = version + 1;
        releasedAt = at;
        releasedBy = Objects.requireNonNull(by, "by");
        releaseReason = reason;
        releaseChannel = ReleaseChannelKind.INTERFACE;
    }

    public SeparationHoldId id() {
        return id;
    }

    public PersonnelCommandId commandId() {
        return commandId;
    }

    public EmployeeId employeeId() {
        return employeeId;
    }

    public HoldStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public @Nullable Instant releasedAt() {
        return releasedAt;
    }

    public @Nullable UserId releasedBy() {
        return releasedBy;
    }

    public @Nullable String releaseReason() {
        return releaseReason;
    }

    public @Nullable ReleaseChannelKind releaseChannel() {
        return releaseChannel;
    }
}
