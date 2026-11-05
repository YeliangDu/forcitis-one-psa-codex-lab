package com.forcitis.one.platform.identity.domain;

import static com.forcitis.one.platform.identity.domain.AuthorizationRequestStatus.CONFIRMED;
import static com.forcitis.one.platform.identity.domain.AuthorizationRequestStatus.PENDING;
import static com.forcitis.one.platform.identity.domain.AuthorizationRequestStatus.REJECTED;
import static com.forcitis.one.platform.identity.domain.AuthorizationRequestStatus.VOIDED;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationRequestStateMachineTest {
    static final Instant AT = Instant.parse("2026-10-15T08:00:00Z");

    static AuthorizationRequest at(AuthorizationRequestStatus status) {
        boolean decided = status == CONFIRMED || status == REJECTED;
        return AuthorizationRequest.restore(new AuthorizationRequestId(TestIds.uuid(1)), AuthorizationRequestKind.REPLACE_ROLES,
                TestAccounts.account(1), AT, TestAccounts.account(2), null, Set.of("security_auditor"), null, new AuthorizationBase(1L, List.of()),
                status.name(), decided ? TestAccounts.account(3) : null, decided ? AT : null, 1, 0);
    }

    @Test
    @DisplayName("confirm, reject and markVoided are each tried from every status")
    void exhaustiveTransitions() {
        new TransitionTable<AuthorizationRequestStatus, AuthorizationRequest>(AuthorizationRequestStatus.class,
                AuthorizationRequestStateMachineTest::at, AuthorizationRequest::status)
                .operation("confirm", r -> r.confirm(TestAccounts.account(3), AT), PENDING, CONFIRMED)
                .operation("reject", r -> r.reject(TestAccounts.account(3), AT), PENDING, REJECTED)
                .operation("markVoided", AuthorizationRequest::markVoided, PENDING, VOIDED, CONFIRMED, VOIDED)
                .verify();
    }
}
