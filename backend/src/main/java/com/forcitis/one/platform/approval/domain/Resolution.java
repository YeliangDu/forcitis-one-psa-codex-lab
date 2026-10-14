package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Set;

/** Never contains nulls or separated people. */
public record Resolution(Set<UserId> candidates, ResolutionKind kind) {

    public Resolution {
        candidates = Set.copyOf(candidates);
    }
}
