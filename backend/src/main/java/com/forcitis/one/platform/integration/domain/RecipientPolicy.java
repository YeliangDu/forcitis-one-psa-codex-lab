package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.WecomUserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Outside production only the allow-listed people receive messages. */
public record RecipientPolicy(boolean allowListOnly, Set<WecomUserId> allowed) {

    public RecipientPolicy {
        allowed = Set.copyOf(allowed);
    }

    public Decision decide(List<WecomUserId> recipients) {
        if (!allowListOnly) {
            return new Decision(recipients, List.of());
        }
        List<WecomUserId> allowedNow = new ArrayList<>();
        List<WecomUserId> skipped = new ArrayList<>();
        for (WecomUserId recipient : recipients) {
            (allowed.contains(recipient) ? allowedNow : skipped).add(recipient);
        }
        return new Decision(allowedNow, skipped);
    }

    public record Decision(List<WecomUserId> allowed, List<WecomUserId> skipped) {

        public Decision {
            allowed = List.copyOf(allowed);
            skipped = List.copyOf(skipped);
        }
    }
}
