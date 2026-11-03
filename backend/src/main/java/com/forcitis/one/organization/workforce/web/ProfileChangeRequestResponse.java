package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.Gender;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestView;
import com.forcitis.one.organization.workforce.api.ProfileChangeStatus;
import com.forcitis.one.organization.workforce.api.ProfileVoidReason;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A profile change request: the whole proposed profile, who asked and how it was decided; the ETag is its version. */
record ProfileChangeRequestResponse(UUID requestId, UUID employeeId, UUID requestedBy, Instant requestedAt, ProfileChangeStatus status, String displayName, @Nullable Gender gender,
                                    @Nullable String email, @Nullable String wecomUserId, @Nullable UUID decidedBy, @Nullable Instant decidedAt, @Nullable ProfileVoidReason voidReason) {

    static ProfileChangeRequestResponse of(ProfileChangeRequestView view) {
        return new ProfileChangeRequestResponse(view.requestId().value(), view.employeeId().value(), view.requestedBy().value(), view.requestedAt(), view.status(), view.displayName(),
                view.gender(), view.email(), view.wecomUserId() == null ? null : view.wecomUserId().value(), view.decidedBy() == null ? null : view.decidedBy().value(), view.decidedAt(), view.voidReason());
    }
}
