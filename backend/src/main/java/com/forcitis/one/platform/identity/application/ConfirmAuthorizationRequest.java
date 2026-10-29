package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Another holder of account-role:update agrees; only the request changes here, the listener applies the change afterwards. */
@Service
public class ConfirmAuthorizationRequest {

    private final CurrentUser currentUser;
    private final AuthorizationRequestRepository requests;
    private final DomainEvents events;
    private final Clock clock;
    private final AccountRepository accounts;

    ConfirmAuthorizationRequest(CurrentUser currentUser, AuthorizationRequestRepository requests, DomainEvents events, Clock clock,
            AccountRepository accounts) {
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.requests = requests;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void execute(AuthorizationRequestId id) {
        AuthorizationRequest request = requests.find(id).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no such authorization request"));
        UserId by = currentUser.userId();
        request.confirm(by, clock.instant(), accounts.find(by).map(UserAccount::roles).orElse(Set.of()));
        requests.save(request);
        events.publish(request);
    }
}
