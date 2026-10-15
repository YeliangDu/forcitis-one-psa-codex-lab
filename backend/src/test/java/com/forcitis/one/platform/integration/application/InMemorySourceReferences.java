package com.forcitis.one.platform.integration.application;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Confirmed source references in memory, recording every allocation and lookup. An allocation for source id n of an employee, org unit or
 * position gets test employee, org unit or position n of the fixtures, so a test can name the own id it expects.
 */
public final class InMemorySourceReferences extends SourceReferences {

    public record Allocation(ExternalSource system, SourceObjectKind kind, long sourceId) {
    }

    public record Lookup(ExternalSource system, SourceObjectKind kind, List<Long> sourceIds) {
    }

    private record Key(ExternalSource system, SourceObjectKind kind, long sourceId) {
    }

    private final Map<Key, UUID> confirmed = new LinkedHashMap<>();
    private final List<Allocation> allocations = new ArrayList<>();
    private final List<Lookup> lookups = new ArrayList<>();

    public InMemorySourceReferences() {
        super(null, null, null);
    }

    public synchronized InMemorySourceReferences confirm(ExternalSource system, SourceObjectKind kind, long sourceId, UUID ownId) {
        confirmed.put(new Key(system, kind, sourceId), ownId);
        return this;
    }

    public synchronized List<Allocation> allocations() {
        return List.copyOf(allocations);
    }

    public synchronized List<Lookup> lookups() {
        return List.copyOf(lookups);
    }

    @Override
    public synchronized UUID allocate(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, long sourceId) {
        allocations.add(new Allocation(sourceSystem, sourceObjectKind, sourceId));
        return confirmed.computeIfAbsent(new Key(sourceSystem, sourceObjectKind, sourceId), k -> fixtureId(k.kind(), k.sourceId()));
    }

    @Override
    public synchronized Map<Long, UUID> findOwnIds(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, Collection<Long> sourceIds) {
        lookups.add(new Lookup(sourceSystem, sourceObjectKind, List.copyOf(sourceIds)));
        Map<Long, UUID> found = new HashMap<>();
        for (Long sourceId : sourceIds) {
            UUID own = confirmed.get(new Key(sourceSystem, sourceObjectKind, sourceId));
            if (own != null) {
                found.put(sourceId, own);
            }
        }
        return found;
    }

    @Override
    public synchronized Optional<UUID> findOwnId(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, long sourceId) {
        return Optional.ofNullable(findOwnIds(sourceSystem, sourceObjectKind, List.of(sourceId)).get(sourceId));
    }

    @Override
    public synchronized OptionalLong findSourceId(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, UUID ownId, LocalDate on) {
        return confirmed.entrySet().stream()
                .filter(e -> e.getKey().system() == sourceSystem && e.getKey().kind() == sourceObjectKind && e.getValue().equals(ownId))
                .mapToLong(e -> e.getKey().sourceId()).findFirst();
    }

    @Override
    public long sourceIdFor(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, UUID ownId, LocalDate on) {
        return findSourceId(sourceSystem, sourceObjectKind, ownId, on)
                .orElseThrow(() -> new IllegalStateException("no " + sourceObjectKind + " reference of " + sourceSystem + " for " + ownId));
    }

    private static UUID fixtureId(SourceObjectKind kind, long sourceId) {
        return switch (kind) {
            case EMPLOYEE -> TestAccounts.employee(sourceId).value();
            case ORG_UNIT -> TestOrgIds.orgUnit(sourceId).value();
            case POSITION -> TestOrgIds.position(sourceId).value();
            default -> UUID.nameUUIDFromBytes((kind + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
        };
    }
}
