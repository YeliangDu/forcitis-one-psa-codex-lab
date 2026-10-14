package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Set;

/**
 * Never contains nulls or separated people. namedApproverInvalid is true when the document named approvers and none of them can act
 * (separated): the node may be skipped, but if every node ends up skipped that way the initiator is the one who can put it right.
 */
public record Resolution(Set<UserId> candidates, ResolutionKind kind, boolean namedApproverInvalid) {

    public Resolution {
        candidates = Set.copyOf(candidates);
    }

    public Resolution(Set<UserId> candidates, ResolutionKind kind) {
        this(candidates, kind, false);
    }
}
