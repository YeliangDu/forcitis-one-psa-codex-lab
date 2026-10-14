package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The initiator is the current user, passed by the owning module after it checked document ownership (I-5); never taken from
 * a client. legalEntityId comes from the owning module and is never defaulted. contentDigest fingerprints the content to
 * approve: a repeat approver is only passed automatically while it is unchanged.
 */
public record ApprovalStartRequest(ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId, UserId initiator,
                                   Set<String> routingFlags, List<ApprovalField> formFields, String contentDigest) {

    public ApprovalStartRequest {
        Objects.requireNonNull(processKey, "processKey");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(contentDigest, "contentDigest");
        if (document.documentType() != processKey.documentType()) {
            throw new IllegalArgumentException(processKey + " approves " + processKey.documentType() + " documents, not "
                    + document.documentType());
        }
        if (contentDigest.isBlank()) {
            throw new IllegalArgumentException("contentDigest must not be blank");
        }
        routingFlags = Set.copyOf(routingFlags);
        formFields = List.copyOf(formFields);
    }
}
