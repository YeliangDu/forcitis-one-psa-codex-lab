package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.kernel.UserId;
import java.util.List;
import java.util.Optional;

public interface AuthorizationRequestRepository {

    Optional<AuthorizationRequest> find(AuthorizationRequestId id);

    List<AuthorizationRequest> findPending();

    /** The requests that still wait and name the person as the subject or as the requester, oldest first. */
    List<AuthorizationRequest> findPendingInvolving(UserId user);

    void insert(AuthorizationRequest opened);

    /** Applies only to the version and status the request was loaded with; otherwise a DomainException (CONFLICT). */
    void save(AuthorizationRequest request);
}
