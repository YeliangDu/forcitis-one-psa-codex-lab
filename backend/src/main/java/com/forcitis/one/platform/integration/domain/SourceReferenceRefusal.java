package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.events.SourceReferenceRegistrationRefused;
import java.util.List;

/** A registration by hand that was refused. Nothing is stored for it; the event is the record, so that the audit trail shows who tried. */
public final class SourceReferenceRefusal extends AggregateRoot {

    private final SourceReferenceId refusalId;

    private SourceReferenceRefusal(SourceReferenceId refusalId) {
        super(0, 0);
        this.refusalId = refusalId;
    }

    public static SourceReferenceRefusal of(SourceReferenceId refusalId, ExternalSource system, SourceObjectKind kind) {
        SourceReferenceRefusal refusal = new SourceReferenceRefusal(refusalId);
        refusal.record((envelopes, version) -> new SourceReferenceRegistrationRefused(envelopes.wrap(refusal.draft(version)), system, kind));
        return refusal;
    }

    private EventDraft draft(long version) {
        return new EventDraft("platform.integration.SourceReferenceRegistrationRefused", 1, "SourceReferenceRefusal", refusalId.value(), version, null,
                null, List.of());
    }
}
