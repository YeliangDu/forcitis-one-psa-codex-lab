package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.KnownLegalEntities;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.IntegrationError;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferenceRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A person registers the accounting organization code of a legal entity; it waits for another person to confirm it. */
@Service
public class RegisterSourceReference {
    private final SourceReferenceRepository references;
    private final KnownLegalEntities legalEntities;
    private final RecordSourceReferenceRefusal refusals;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final CurrentUser currentUser;

    RegisterSourceReference(SourceReferenceRepository references, KnownLegalEntities legalEntities, RecordSourceReferenceRefusal refusals,
                            DomainEvents events, IdGenerator ids, CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.references = references;
        this.legalEntities = legalEntities;
        this.refusals = refusals;
        this.events = events;
        this.ids = ids;
    }

    @Transactional
    public SourceReferenceView execute(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, long sourceId, UUID ownId,
                                       LocalDate effectiveFrom) {
        UserId registeredBy = currentUser.userId();
        try {
            SourceReference.requireRegistrationAllowed(sourceSystem, sourceObjectKind);
        } catch (DomainException refused) {
            refusals.execute(sourceSystem, sourceObjectKind);
            throw refused;
        }
        SourceReference reference = SourceReference.register(new SourceReferenceId(ids.next()), sourceSystem, sourceObjectKind, new SourceObjectId(sourceId), new OwnObjectId(ownId),
                effectiveFrom, registeredBy);
        if (!legalEntities.isKnown(new LegalEntityId(ownId))) {
            throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_INVALID, ErrorKind.UNPROCESSABLE,
                    "ownId names no legal entity");
        }
        references.insert(reference);
        events.publish(reference);
        return SourceReferenceView.of(reference);
    }
}
