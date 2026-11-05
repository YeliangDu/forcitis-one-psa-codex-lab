package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.LegalEntityId;
import org.jspecify.annotations.Nullable;

/**
 * One employment relationship of a person as it stands on a day (ADR-0086, design 6.2): the employer, whether HR has confirmed it (a migrated attribution is
 * not confirmed until HR says so, and no consumer acts on an unconfirmed employer), the phase and the unpaid-leave flag. The phase is empty while the
 * relationship is out of service.
 */
public record EmploymentRelationshipView(EmploymentRelationshipId id, LegalEntityId employerLegalEntityId, boolean employerConfirmed, @Nullable EmploymentPhaseKind phaseKind,
                                         boolean onUnpaidLeave) {
}
