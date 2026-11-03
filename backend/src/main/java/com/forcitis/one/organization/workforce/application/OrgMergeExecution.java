package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The due-date step of a merge (ADR-0068 item 4 and the merge notes of its attachment). It starts the merge, moves the people one transaction each
 * and finishes it. A person who cannot be moved leaves the others moved and the merge in progress; the run reports the first failure after it has tried
 * everybody, and a later run continues with whoever is left. Not itself a transaction: the steps are, so that one person's refusal never undoes another's move.
 */
@SystemActorName("organization.workforce.org.structure.merge.execute")
@Component
class OrgMergeExecution {
    static final String ACTOR = OrgMergeExecution.class.getAnnotation(SystemActorName.class).value();
    private static final Logger LOG = LoggerFactory.getLogger(OrgMergeExecution.class);

    private final OrgMergeSteps steps;
    private final Clock clock;

    OrgMergeExecution(OrgMergeSteps steps, Clock clock) {
        this.steps = steps;
        this.clock = clock;
    }

    void execute(OrgStructureCommandId id) {
        try (WorkContext.Scope scope = WorkContext.enter(Uuids.timeOrdered(clock), null, ACTOR)) {
            UUID actor = SystemActors.idOf(ACTOR);
            List<EmployeeId> remaining = steps.start(id, actor);
            RuntimeException firstFailure = null;
            for (EmployeeId employee : remaining) {
                try {
                    steps.move(id, employee, actor);
                } catch (RuntimeException refused) {
                    LOG.warn("organization.workforce merge {} could not move an employee: {}", id, refused.getMessage());
                    if (firstFailure == null) {
                        firstFailure = refused;
                    }
                }
            }
            steps.settle(id);
            if (firstFailure != null) {
                throw firstFailure;
            }
            steps.finish(id, actor);
        }
    }
}
