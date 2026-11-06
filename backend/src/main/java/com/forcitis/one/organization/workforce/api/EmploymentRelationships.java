package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The employment relationships of a person (ADR-0086, design 6.2). A person may have several in service on one day, so there is no read that returns one employer;
 * a consumer that needs the employer of a fact keeps the relationship id and asks {@link #employerOn}.
 */
public interface EmploymentRelationships {

    /** The relationships in service on the day. */
    List<EmploymentRelationshipView> on(EmployeeId employee, LocalDate date);

    /** The relationship as it stands now (its latest version); empty when there is none. */
    Optional<EmploymentRelationshipView> get(EmploymentRelationshipId id);

    /** The relationship as it stood on the day, with the employer of that day (succession is not built yet, so it is the base employer); empty when it had no version covering the day. */
    Optional<EmploymentRelationshipView> employerOn(EmploymentRelationshipId id, LocalDate date);
}
