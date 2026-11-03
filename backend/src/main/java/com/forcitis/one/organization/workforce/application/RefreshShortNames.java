package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.ShortNameSource;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A short name is not a source column, so no source read touches it. */
@Service
public class RefreshShortNames {
    private static final Logger LOG = LoggerFactory.getLogger(RefreshShortNames.class);

    private final ShortNameSource source;
    private final OrgSnapshotRepository snapshots;
    private final Clock clock;
    private final Counter failures;

    RefreshShortNames(ShortNameSource source, OrgSnapshotRepository snapshots, Clock clock, MeterRegistry registry) {
        this.source = source;
        this.snapshots = snapshots;
        this.clock = clock;
        this.failures = Counter.builder("forcitis.organization.workforce.short.name.refresh.failures")
                .description("Short name refreshes that could not read the old system and left the names as they were")
                .register(registry);
    }

    @Transactional
    public int execute() {
        Map<OrgUnitId, String> names;
        try {
            names = source.fetchShortNames();
        } catch (ExternalSystemUnavailableException unavailable) {
            LOG.warn("organization.workforce short name refresh skipped: {}", unavailable.getMessage());
            failures.increment();
            return 0;
        }
        Map<OrgUnitId, String> normalized = new HashMap<>();
        names.forEach((unit, name) -> normalized.put(unit, name == null || name.isBlank() ? null : name));
        return snapshots.updateShortNames(normalized, clock.instant());
    }
}
