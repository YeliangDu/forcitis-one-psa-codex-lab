package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import com.forcitis.one.organization.workforce.api.ProfileChangeStatus;
import com.forcitis.one.organization.workforce.api.ProfileVoidReason;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("#980 ProfileChangeRequest: a request is decided once, never before it was made, and a rejection needs its reason")
class ProfileChangeRequestTest {
    static final Instant AT = Instant.parse("2026-11-03T08:00:00Z");
    static final UserId REQUESTER = TestAccounts.account(1);
    static final UserId SECOND = TestAccounts.account(2);

    private static ProfileChangeRequest request() {
        PersonalProfile base = new PersonalProfile("Synthetic name", null, null, new WecomUserId("old"));
        PersonalProfile after = new PersonalProfile("Synthetic name", null, null, new WecomUserId("new"));
        return ProfileChangeRequest.open(new ProfileChangeRequestId(UUID.randomUUID()), new EmployeeId(UUID.randomUUID()), REQUESTER, AT, base, after);
    }

    @Test
    void aPendingRequestIsConfirmedOnceAndTheVersionMoves() {
        ProfileChangeRequest request = request();

        request.confirm(SECOND, AT.plusSeconds(5));

        assertThat(request.status()).isEqualTo(ProfileChangeStatus.CONFIRMED);
        assertThat(request.decidedBy()).isEqualTo(SECOND);
        assertThat(request.version()).isEqualTo(1);
        assertThatThrownBy(() -> request.reject(SECOND, "late", AT.plusSeconds(6))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> request.voidWith(ProfileVoidReason.BASE_CHANGED, SECOND, AT.plusSeconds(6))).isInstanceOf(DomainException.class);
    }

    @Test
    void aDecisionIsNeverBeforeTheRequest() {
        ProfileChangeRequest request = request();

        assertThatThrownBy(() -> request.confirm(SECOND, AT.minusSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThat(request.status()).isEqualTo(ProfileChangeStatus.PENDING);
    }

    @Test
    void aRejectionNeedsAReasonOfOneToFiveHundredCharacters() {
        for (String bad : new String[] {null, "", "   ", "x".repeat(501)}) {
            ProfileChangeRequest request = request();
            assertThatThrownBy(() -> request.reject(SECOND, bad, AT.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
            assertThat(request.status()).isEqualTo(ProfileChangeStatus.PENDING);
        }
        ProfileChangeRequest withdrawn = request();
        withdrawn.reject(REQUESTER, "x".repeat(500), AT.plusSeconds(1));
        assertThat(withdrawn.status()).isEqualTo(ProfileChangeStatus.REJECTED);
        assertThat(withdrawn.decisionReason()).hasSize(500);
    }

    @Test
    void aVoidKeepsItsReason() {
        ProfileChangeRequest request = request();

        request.voidWith(ProfileVoidReason.WECOM_USER_ID_TAKEN, SECOND, AT.plusSeconds(2));

        assertThat(request.status()).isEqualTo(ProfileChangeStatus.VOIDED);
        assertThat(request.voidReason()).isEqualTo(ProfileVoidReason.WECOM_USER_ID_TAKEN);
    }
}
