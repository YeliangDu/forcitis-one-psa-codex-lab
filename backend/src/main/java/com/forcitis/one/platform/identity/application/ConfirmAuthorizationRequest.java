package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Another holder of account-role:update agrees; only the request changes here, the listener applies the change afterwards. */
@Service
public class ConfirmAuthorizationRequest {

    private final CurrentUser currentUser;
    private final AuthorizationRequestRepository requests;
    private final DomainEvents events;
    private final Clock clock;

    ConfirmAuthorizationRequest(CurrentUser currentUser, AuthorizationRequestRepository requests, DomainEvents events, Clock clock) {
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
        request.confirm(by, clock.instant());
        requests.save(request);
        events.publish(request);
    }
}
