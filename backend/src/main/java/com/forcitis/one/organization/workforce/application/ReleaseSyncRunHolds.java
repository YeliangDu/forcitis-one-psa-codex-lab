package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.HoldKind;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each kind of hold is lifted only by someone entitled to that kind (ADR-0028): the shrink guard by the sync-run executor, the
 * administrator separation by a retained super_admin or security auditor. The run stays held until every kind is lifted.
 */
@Service
class ReleaseSyncRunHolds {
    private final SyncRunRepository runs;
    private final DomainEvents events;
    private final SeparationReview separations;

    ReleaseSyncRunHolds(SyncRunRepository runs, DomainEvents events, SeparationReview separations) {
        this.runs = runs;
        this.events = events;
        this.separations = separations;
    }

    record Lifted(SyncRun run, SeparationReview.Verdict review) {
    }

    /** Empty when the run is not there; throws when the actor may lift nothing; a refusal comes back as the reason. */
    @Transactional
    public Optional<Lifted> execute(SyncRunId syncRunId, UserId actor, boolean mayLiftShrinkGuard, boolean mayLiftSeparation) {
        Optional<SyncRun> found = runs.find(syncRunId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SyncRun run = found.get();
        List<HoldKind> lifting = new ArrayList<>();
        if (mayLiftShrinkGuard && waiting(run, HoldKind.SHRINK_GUARD)) {
            lifting.add(HoldKind.SHRINK_GUARD);
        }
        SeparationReview.Verdict review = separations.review(run.heldSeparations(), actor);
        if (mayLiftSeparation && waiting(run, HoldKind.ADMINISTRATOR_SEPARATION) && review.actorRetained()) {
            if (review.remainingAdministrators() < 1) {
                throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED, ErrorKind.CONFLICT,
                        "no usable super_admin would remain; only the operations command can release this run");
            }
            lifting.add(HoldKind.ADMINISTRATOR_SEPARATION);
        }
        if (lifting.isEmpty()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_FORBIDDEN, ErrorKind.FORBIDDEN,
                    "you may not lift any hold that is still waiting on sync run " + syncRunId.value());
        }
        for (HoldKind kind : lifting) {
            run.releaseKind(kind, actor);
        }
        runs.save(run);
        events.publish(run);
        return Optional.of(new Lifted(run, review));
    }

    private static boolean waiting(SyncRun run, HoldKind kind) {
        return run.holdKinds().contains(kind) && !run.releasedKinds().contains(kind);
    }
}
