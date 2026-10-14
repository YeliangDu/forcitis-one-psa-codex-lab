package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.UserId;
import java.util.List;
import java.util.Set;

/** The initiator is the current user, passed by the owning module after it checked document ownership (I-5); never taken from a client. */
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
