package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferenceRefusal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Own transaction: the refused request rolls back, the record of the attempt must not. */
@Service
class RecordSourceReferenceRefusal {
    private final DomainEvents events;
    private final IdGenerator ids;

    RecordSourceReferenceRefusal(DomainEvents events, IdGenerator ids) {
        this.events = events;
        this.ids = ids;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind) {
        events.publish(SourceReferenceRefusal.of(new SourceReferenceId(ids.next()), sourceSystem, sourceObjectKind));
    }
}
