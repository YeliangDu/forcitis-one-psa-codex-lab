package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Set;

/** Candidate set of one node, without nulls and without separated people; kind tells how it was reached. */
public record Resolution(Set<UserId> candidates, ResolutionKind kind) {

    public Resolution {
        candidates = Set.copyOf(candidates);
    }
}
