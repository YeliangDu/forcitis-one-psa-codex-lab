package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.ShortNameSource;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A short name is not a source column, so the sync never touches it. */
@Service
public class RefreshShortNames {
    private static final Logger LOG = LoggerFactory.getLogger(RefreshShortNames.class);

    private final ShortNameSource source;
    private final OrgSnapshotRepository snapshots;
    private final Clock clock;

    RefreshShortNames(ShortNameSource source, OrgSnapshotRepository snapshots, Clock clock) {
        this.source = source;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @Transactional
    public int execute() {
        Map<OrgUnitId, String> names;
        try {
            names = source.fetchShortNames();
        } catch (ExternalSystemUnavailableException unavailable) {
            LOG.warn("organization.workforce short name refresh skipped: {}", unavailable.getMessage());
            return 0;
        }
        return snapshots.updateShortNames(names, clock.instant());
    }
}
