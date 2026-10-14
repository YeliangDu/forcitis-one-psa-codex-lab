package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.ValueObject;
import java.time.Instant;
import java.util.Optional;

/** One saved version of a rule's template; a version never changes once saved, only its confirmations are added. */
@ValueObject
public record NumberingTemplateView(int versionNumber, String pattern, ResetPolicy resetPolicy, long startValue, long capacity,
        Optional<UserId> author, Instant authoredAt, Optional<UserId> confirmer, Optional<UserId> financeConfirmer) {
}
