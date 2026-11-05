package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Reads the employment relationships and their current versions (ADR-0086). */
public interface EmploymentRelationshipQueries {

    List<EmploymentRelationshipView> inServiceOn(EmployeeId employee, LocalDate day);

    Optional<EmploymentRelationshipView> latest(EmploymentRelationshipId id);

    Optional<EmploymentRelationshipView> versionOn(EmploymentRelationshipId id, LocalDate day);
}
