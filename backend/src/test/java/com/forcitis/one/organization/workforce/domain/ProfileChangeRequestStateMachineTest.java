package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.api.ProfileChangeStatus.CONFIRMED;
import static com.forcitis.one.organization.workforce.api.ProfileChangeStatus.PENDING;
import static com.forcitis.one.organization.workforce.api.ProfileChangeStatus.REJECTED;
import static com.forcitis.one.organization.workforce.api.ProfileChangeStatus.VOIDED;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import com.forcitis.one.organization.workforce.api.ProfileChangeStatus;
import com.forcitis.one.organization.workforce.api.ProfileVoidReason;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileChangeRequestStateMachineTest {
    static final Instant REQUESTED = Instant.parse("2026-10-14T02:00:00Z");
    static final Instant DECIDED = REQUESTED.plusSeconds(60);
    static final UserId SECOND = new UserId(TestIds.uuid(5));

    static ProfileChangeRequest at(ProfileChangeStatus status) {
        boolean decided = status != PENDING;
        return ProfileChangeRequest.restore(new ProfileChangeRequestId(TestIds.uuid(1)), new EmployeeId(TestIds.uuid(2)), new UserId(TestIds.uuid(3)), REQUESTED,
                new PersonalProfile("Synthetic name", null, null, new WecomUserId("old")), new PersonalProfile("Synthetic name", null, null, new WecomUserId("new")),
                status, 1, decided ? SECOND : null, decided ? DECIDED : null, status == VOIDED ? ProfileVoidReason.BASE_CHANGED : null, status == REJECTED ? "no" : null);
    }

    @Test
    @DisplayName("confirm, reject and voidWith are tried from every status: each leaves only PENDING")
    void exhaustiveTransitions() {
        new TransitionTable<ProfileChangeStatus, ProfileChangeRequest>(ProfileChangeStatus.class, ProfileChangeRequestStateMachineTest::at, ProfileChangeRequest::status)
                .operation("confirm", r -> r.confirm(SECOND, DECIDED), PENDING, CONFIRMED)
                .operation("reject", r -> r.reject(SECOND, "no", DECIDED), PENDING, REJECTED)
                .operation("voidWith", r -> r.voidWith(ProfileVoidReason.WECOM_USER_ID_TAKEN, SECOND, DECIDED), PENDING, VOIDED)
                .verify();
    }
}
