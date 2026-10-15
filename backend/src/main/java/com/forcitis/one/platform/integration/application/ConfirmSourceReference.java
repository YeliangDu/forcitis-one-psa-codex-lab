package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferenceRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Four eyes: not the person who registered it. The reference in effect ends where the confirmed one starts, in the same transaction. */
@Service
public class ConfirmSourceReference {
    private final SourceReferenceRepository references;
    private final DomainEvents events;
    private final Clock clock;

    ConfirmSourceReference(SourceReferenceRepository references, DomainEvents events, Clock clock) {
        this.references = references;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public SourceReferenceView execute(UUID sourceReferenceId, UserId confirmer) {
        SourceReference reference = references.findForUpdate(new SourceReferenceId(sourceReferenceId)).orElseThrow(() -> new SourceReferenceNotFound(sourceReferenceId));
        reference.confirm(confirmer, clock.instant());
        if (reference.kind().hasPeriod()) {
            references.findOpenConfirmedForUpdate(reference.system(), reference.kind(), reference.ownId()).ifPresent(inEffect -> {
                inEffect.supersede(reference.effectiveFrom());
                references.save(inEffect);
                events.publish(inEffect);
            });
        }
        references.save(reference);
        events.publish(reference);
        return SourceReferenceView.of(reference);
    }
}
