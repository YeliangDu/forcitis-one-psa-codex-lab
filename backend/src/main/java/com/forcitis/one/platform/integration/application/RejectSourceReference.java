package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferenceRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A rejected reference changes nothing that is in effect. */
@Service
public class RejectSourceReference {
    private final SourceReferenceRepository references;
    private final DomainEvents events;
    private final Clock clock;
    private final CurrentUser currentUser;

    RejectSourceReference(SourceReferenceRepository references, DomainEvents events, Clock clock, CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.references = references;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public SourceReferenceView execute(UUID sourceReferenceId) {
        UserId rejecter = currentUser.userId();
        SourceReference reference = references.findForUpdate(new SourceReferenceId(sourceReferenceId)).orElseThrow(() -> new SourceReferenceNotFound(sourceReferenceId));
        reference.reject(rejecter, clock.instant());
        references.save(reference);
        events.publish(reference);
        return SourceReferenceView.of(reference);
    }
}
