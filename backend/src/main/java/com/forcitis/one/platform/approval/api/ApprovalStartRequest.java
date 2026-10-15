package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The initiator is the user who submitted the document, as the owning module recorded it (I-5); never taken from a client.
 * legalEntityId comes from the owning module and is never defaulted. round is the round the owning module wants started: asking
 * again for the same round is a retry and returns the same instance (D-1a-78). trigger is the id of the event that asked for it.
 * contentHash fingerprints the content to approve; summary is what cards show.
 */
public record ApprovalStartRequest(ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId, EmployeeId initiator,
                                   RoundNumber round, UUID trigger, Set<String> routingFlags, List<ApprovalField> formFields,
                                   ContentHash contentHash, ApprovalSummary summary) {

    public ApprovalStartRequest {
        Objects.requireNonNull(processKey, "processKey");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(round, "round");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(summary, "summary");
        if (document.documentType() != processKey.documentType()) {
            throw new IllegalArgumentException(processKey + " approves " + processKey.documentType() + " documents, not "
                    + document.documentType());
        }
        routingFlags = Set.copyOf(routingFlags);
        formFields = List.copyOf(formFields);
    }
}
