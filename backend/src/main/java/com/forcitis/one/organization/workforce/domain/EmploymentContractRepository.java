package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import java.util.List;
import java.util.Optional;

/** Storage of labour contracts. Every method joins the caller's transaction. The state-changing writes arrive with the WC2 commands. */
public interface EmploymentContractRepository {

    Optional<EmploymentContract> find(EmploymentContractId id);

    /** The relationship's contracts, oldest term start first, voided ones included. */
    List<EmploymentContract> ofRelationship(EmploymentRelationshipId relationship);

    /** Stores a new contract; the table accepts it only as a DRAFT. */
    void insert(EmploymentContract contract);
}
