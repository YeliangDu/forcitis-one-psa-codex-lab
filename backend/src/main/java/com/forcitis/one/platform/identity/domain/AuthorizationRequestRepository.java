package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import java.util.List;
import java.util.Optional;

public interface AuthorizationRequestRepository {

    Optional<AuthorizationRequest> find(AuthorizationRequestId id);

    List<AuthorizationRequest> findPending();

    void insert(AuthorizationRequest opened);

    /** Applies only to the version and status the request was loaded with; otherwise a DomainException (CONFLICT). */
    void save(AuthorizationRequest request);
}
