package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import java.util.List;
import java.util.Optional;

/** Storage of separation requests. Every method joins the caller's transaction. The state-changing writes arrive with the WC3 commands. */
public interface SeparationRequestRepository {

    Optional<SeparationRequest> find(SeparationRequestId id);

    /** The relationship's requests, oldest first, withdrawn and rejected ones included. */
    List<SeparationRequest> ofRelationship(EmploymentRelationshipId relationship);

    /** Stores a new request; the table accepts a human-filed one only as REQUESTED and a SYSTEM one only as APPROVED or COMPLETED. */
    void insert(SeparationRequest request);
}
