package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.UserId;
import java.util.Set;

/**
 * Ask for an approval. routingFlags are named booleans pre-computed by the owning module
 * (e.g. "needs-seal"); BPMN only reads them, it never evaluates business rules itself.
 */
public record ApprovalRequest(ProcessKey processKey, String businessKey, UserId initiator, Set<String> routingFlags) {

    public ApprovalRequest {
        routingFlags = Set.copyOf(routingFlags);
    }
}
