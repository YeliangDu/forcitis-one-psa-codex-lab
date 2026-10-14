package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.UserId;
import java.util.List;
import java.util.Set;

/**
 * Ask for an approval. The initiator is the CURRENT user, passed by the owning module after it has checked that the
 * caller owns the business document (I-5, RULE-265): approval never takes an initiator from a client.
 * routingFlags are named booleans pre-computed by the owning module (e.g. "needs-seal"); BPMN only reads them.
 * formFields carry the business values assignment rules need (RULE-258..260).
 */
public record ApprovalRequest(ProcessKey processKey, String businessKey, UserId initiator, Set<String> routingFlags,
                              List<ApprovalField> formFields) {

    public ApprovalRequest {
        routingFlags = Set.copyOf(routingFlags);
        formFields = List.copyOf(formFields);
    }

    public ApprovalRequest(ProcessKey processKey, String businessKey, UserId initiator, Set<String> routingFlags) {
        this(processKey, businessKey, initiator, routingFlags, List.of());
    }
}
