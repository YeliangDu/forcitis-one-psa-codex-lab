package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import com.forcitis.one.organization.workforce.api.ProfileChangeStatus;
import com.forcitis.one.organization.workforce.api.ProfileVoidReason;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A profile change that waits for a second person (design note wecom-rebind-second-person.md section 4): the whole proposed profile and the profile it was made
 * against. It ends once: CONFIRMED (applied by the confirming transaction), REJECTED (by another person, or withdrawn by the requester) or VOIDED (the base moved or
 * the proposed WeCom user id was taken). The decision columns are written together (the table's checks and trigger); that a decision is never dated before the request is held by this class alone.
 */
public final class ProfileChangeRequest {
    public static final int MAX_REASON_LENGTH = 500;

    private static final StateMachine<ProfileChangeStatus> MACHINE = StateMachine.of(ProfileChangeStatus.class)
            .allow(ProfileChangeStatus.PENDING, ProfileChangeStatus.CONFIRMED, ProfileChangeStatus.REJECTED, ProfileChangeStatus.VOIDED)
            .build();

    private final ProfileChangeRequestId id;
    private final EmployeeId employeeId;
    private final UserId requestedBy;
    private final Instant requestedAt;
    private final PersonalProfile base;
    private final PersonalProfile proposed;
    private ProfileChangeStatus status;
    private long version;
    private @Nullable UserId decidedBy;
    private @Nullable Instant decidedAt;
    private @Nullable ProfileVoidReason voidReason;
    private @Nullable String decisionReason;

    private ProfileChangeRequest(ProfileChangeRequestId id, EmployeeId employeeId, UserId requestedBy, Instant requestedAt, PersonalProfile base, PersonalProfile proposed,
                                 ProfileChangeStatus status, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.base = Objects.requireNonNull(base, "base");
        this.proposed = Objects.requireNonNull(proposed, "proposed");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    public static ProfileChangeRequest open(ProfileChangeRequestId id, EmployeeId employeeId, UserId requestedBy, Instant at, PersonalProfile base, PersonalProfile proposed) {
        return new ProfileChangeRequest(id, employeeId, requestedBy, at, base, proposed, ProfileChangeStatus.PENDING, 0);
    }

    public static ProfileChangeRequest restore(ProfileChangeRequestId id, EmployeeId employeeId, UserId requestedBy, Instant requestedAt, PersonalProfile base, PersonalProfile proposed,
                                               ProfileChangeStatus status, long version, @Nullable UserId decidedBy, @Nullable Instant decidedAt,
                                               @Nullable ProfileVoidReason voidReason, @Nullable String decisionReason) {
        ProfileChangeRequest request = new ProfileChangeRequest(id, employeeId, requestedBy, requestedAt, base, proposed, status, version);
        request.decidedBy = decidedBy;
        request.decidedAt = decidedAt;
        request.voidReason = voidReason;
        request.decisionReason = decisionReason;
        return request;
    }

    public void confirm(UserId by, Instant at) {
        decide(ProfileChangeStatus.CONFIRMED, by, at);
    }

    /** The reason is required (1 to 500 characters, not blank); the requester's own withdrawal is a rejection too. */
    public void reject(UserId by, String reason, Instant at) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("a rejection needs a reason of 1 to " + MAX_REASON_LENGTH + " characters that is not blank");
        }
        decide(ProfileChangeStatus.REJECTED, by, at);
        decisionReason = reason;
    }

    public void voidWith(ProfileVoidReason why, UserId by, Instant at) {
        decide(ProfileChangeStatus.VOIDED, by, at);
        voidReason = Objects.requireNonNull(why, "why");
    }

    private void decide(ProfileChangeStatus to, UserId by, Instant at) {
        if (at.isBefore(requestedAt)) {
            throw new IllegalStateException("a request is not decided before it was made");
        }
        status = MACHINE.require(status, to);
        decidedBy = Objects.requireNonNull(by, "by");
        decidedAt = at;
        version = version + 1;
    }

    public ProfileChangeRequestId id() {
        return id;
    }

    public EmployeeId employeeId() {
        return employeeId;
    }

    public UserId requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public PersonalProfile base() {
        return base;
    }

    public PersonalProfile after() {
        return proposed;
    }

    public ProfileChangeStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public @Nullable UserId decidedBy() {
        return decidedBy;
    }

    public @Nullable Instant decidedAt() {
        return decidedAt;
    }

    public @Nullable ProfileVoidReason voidReason() {
        return voidReason;
    }

    public @Nullable String decisionReason() {
        return decisionReason;
    }
}
