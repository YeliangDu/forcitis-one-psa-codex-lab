package com.forcitis.one.platform.integration.application;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** What the adapters of the external systems use: an own id for a source object on first sight, and the source id back for an own id on the way out. */
@Component
public class SourceReferences {
    private final AllocateOwnId allocateOwnId;
    private final ResolveSourceId resolveSourceId;

    SourceReferences(AllocateOwnId allocateOwnId, ResolveSourceId resolveSourceId) {
        this.allocateOwnId = allocateOwnId;
        this.resolveSourceId = resolveSourceId;
    }

    public UUID allocate(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, long sourceId) {
        return allocateOwnId.execute(sourceSystem, sourceObjectKind, new SourceObjectId(sourceId)).value();
    }

    public long sourceIdFor(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, UUID ownId, LocalDate on) {
        return resolveSourceId.execute(sourceSystem, sourceObjectKind, new OwnObjectId(ownId), on).value();
    }
}
