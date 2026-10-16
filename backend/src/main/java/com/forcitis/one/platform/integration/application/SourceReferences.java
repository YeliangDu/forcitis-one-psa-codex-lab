package com.forcitis.one.platform.integration.application;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReferenceQueries;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the adapters of the external systems use: an own id for a source object on first sight, the own ids of the objects already seen
 * (looked up, never allocated), and the source id back for an own id on the way out.
 */
@Component
public class SourceReferences {
    private final AllocateOwnId allocateOwnId;
    private final ResolveSourceId resolveSourceId;
    private final SourceReferenceQueries queries;

    SourceReferences(AllocateOwnId allocateOwnId, ResolveSourceId resolveSourceId, SourceReferenceQueries queries) {
        this.allocateOwnId = allocateOwnId;
        this.resolveSourceId = resolveSourceId;
        this.queries = queries;
    }

    public UUID allocate(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, long sourceId) {
        return allocateOwnId.execute(sourceSystem, sourceObjectKind, new SourceObjectId(sourceId)).value();
    }

    /** The own ids of the confirmed references among the source ids; a source id without one is left out, nothing is allocated. */
    @Transactional(readOnly = true)
    public Map<Long, UUID> findOwnIds(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, Collection<Long> sourceIds) {
        Map<Long, UUID> found = new HashMap<>();
        queries.findOwnIds(sourceSystem, sourceObjectKind, sourceIds.stream().map(SourceObjectId::new).toList())
                .forEach((source, own) -> found.put(source.value(), own.value()));
        return found;
    }

    /** The own id of the confirmed reference of one source object, looked up and never allocated. */
    @Transactional(readOnly = true)
    public Optional<UUID> findOwnId(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, long sourceId) {
        return queries.findOwnId(sourceSystem, sourceObjectKind, new SourceObjectId(sourceId)).map(OwnObjectId::value);
    }

    /** The source id of the confirmed reference in effect on the day, or empty; for a caller that answers "unavailable" itself. */
    @Transactional(readOnly = true)
    public OptionalLong findSourceId(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, UUID ownId, LocalDate on) {
        return queries.findSourceId(sourceSystem, sourceObjectKind, new OwnObjectId(ownId), on).map(id -> OptionalLong.of(id.value()))
                .orElseGet(OptionalLong::empty);
    }

    public long sourceIdFor(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, UUID ownId, LocalDate on) {
        return resolveSourceId.execute(sourceSystem, sourceObjectKind, new OwnObjectId(ownId), on).value();
    }
}
