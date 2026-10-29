package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Any holder of account-role:update, the requester included (withdrawing), may refuse a request that is still waiting. */
@Service
public class RejectAuthorizationRequest {

    private final CurrentUser currentUser;
    private final AuthorizationRequestRepository requests;
    private final DomainEvents events;
    private final Clock clock;

    RejectAuthorizationRequest(CurrentUser currentUser, AuthorizationRequestRepository requests, DomainEvents events, Clock clock) {
        this.currentUser = currentUser;
        this.requests = requests;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void execute(AuthorizationRequestId id) {
        AuthorizationRequest request = requests.find(id).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no such authorization request"));
        request.reject(currentUser.userId(), clock.instant());
        requests.save(request);
        events.publish(request);
    }
}
