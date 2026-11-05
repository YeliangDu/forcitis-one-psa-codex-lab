package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.events.AuthorizationConfirmed;
import com.forcitis.one.platform.identity.events.AuthorizationRejected;
import com.forcitis.one.platform.identity.events.AuthorizationOpened;
import com.forcitis.one.platform.identity.events.AuthorizationVoided;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SensitiveGrantTwoPerson (domain): the requester and the recipient never confirm")
class AuthorizationRequestTest {

    static final Instant AT = Instant.parse("2026-10-15T08:00:00Z");
    static final UserId REQUESTER = TestAccounts.account(1);
    static final UserId RECIPIENT = TestAccounts.account(2);
    static final UserId OTHER = TestAccounts.account(3);

    static AuthorizationRequest request() {
        return AuthorizationRequest.replaceRoles(new AuthorizationRequestId(UUID.randomUUID()), REQUESTER, AT, RECIPIENT, Set.of("security_auditor"), new AuthorizationBase(1L, List.of()));
    }

    private static IdentityError refusal(Runnable action) {
        try {
            action.run();
        } catch (DomainException refused) {
            return (IdentityError) refused.errorCode();
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    void anotherPersonConfirmsAndOnlyOnce() {
        AuthorizationRequest request = request();

        assertThat(refusal(() -> request.confirm(REQUESTER, AT))).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED);
        assertThat(refusal(() -> request.confirm(RECIPIENT, AT))).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED);
        request.confirm(OTHER, AT);

        assertThat(request.status()).isEqualTo(AuthorizationRequestStatus.CONFIRMED);
        assertThat(request.decidedBy()).isEqualTo(OTHER);
        assertThat(refusal(() -> request.confirm(OTHER, AT))).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
        assertThat(refusal(() -> request.reject(OTHER, AT))).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
        assertThat(request.drainEvents(TestEnvelopes.INSTANCE)).satisfiesExactly(e -> assertThat(e).isInstanceOf(AuthorizationOpened.class),
                e -> assertThat(e).isInstanceOf(AuthorizationConfirmed.class));
    }

    @Test
    void aWaitingRequestEndsVoidedWhenItsSubjectOrRequesterSeparates() {
        AuthorizationRequest request = request();

        request.markVoided();

        assertThat(request.status()).isEqualTo(AuthorizationRequestStatus.VOIDED);
        assertThat(request.decidedBy()).as("nobody decided it").isNull();
        assertThat(refusal(() -> request.confirm(OTHER, AT))).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
        assertThat(refusal(request::markVoided)).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
    }

    @Test
    void aRefusedRequestCannotBeVoided() {
        AuthorizationRequest request = request();
        request.reject(OTHER, AT);

        assertThat(refusal(request::markVoided)).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
    }

    @Test
    void aConfirmedRequestCanBeVoidedOnce() {
        AuthorizationRequest request = request();
        request.confirm(OTHER, AT);
        request.markVoided();

        assertThat(request.status()).isEqualTo(AuthorizationRequestStatus.VOIDED);
        assertThat(request.decidedBy()).as("the confirmation stays on record").isEqualTo(OTHER);
        assertThat(request.drainEvents(TestEnvelopes.INSTANCE)).satisfiesExactly(e -> assertThat(e).isInstanceOf(AuthorizationOpened.class),
                e -> assertThat(e).isInstanceOf(AuthorizationConfirmed.class), e -> assertThat(e).isInstanceOf(AuthorizationVoided.class));
        assertThat(refusal(request::markVoided)).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
    }

    @Test
    void anyoneMayRefuseOrWithdraw() {
        AuthorizationRequest request = request();

        request.reject(REQUESTER, AT);

        assertThat(request.status()).isEqualTo(AuthorizationRequestStatus.REJECTED);
        assertThat(request.drainEvents(TestEnvelopes.INSTANCE)).last().isInstanceOf(AuthorizationRejected.class);
        assertThat(refusal(() -> request.confirm(OTHER, AT))).isEqualTo(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
    }
}
