package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import org.jspecify.annotations.Nullable;

/** One employment relationship of an employee as the rules need it (ADR-0086): who the employer is, the relationship it succeeds and whether the employer is confirmed. */
public record RelationshipRow(EmploymentRelationshipId id, LegalEntityId employerLegalEntityId, @Nullable EmploymentRelationshipId predecessorId, boolean employerConfirmed) {
}
