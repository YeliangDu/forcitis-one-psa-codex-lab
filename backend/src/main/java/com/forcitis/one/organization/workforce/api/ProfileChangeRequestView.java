package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A profile change request as it stands: the proposed profile (whole, not a patch), who asked, and how it was decided. The version is the ETag. */
public record ProfileChangeRequestView(ProfileChangeRequestId requestId, EmployeeId employeeId, UserId requestedBy, Instant requestedAt, ProfileChangeStatus status,
                                       String displayName, @Nullable Gender gender, @Nullable String email, @Nullable WecomUserId wecomUserId,
                                       @Nullable UserId decidedBy, @Nullable Instant decidedAt, @Nullable ProfileVoidReason voidReason, long version) {
}
