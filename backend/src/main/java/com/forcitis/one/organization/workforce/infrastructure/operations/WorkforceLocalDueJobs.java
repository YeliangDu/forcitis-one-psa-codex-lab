package com.forcitis.one.organization.workforce.infrastructure.operations;

import com.forcitis.one.kernel.LocalDueJobs;
import com.forcitis.one.organization.workforce.application.AttributionCountersignDeadlineJob;
import com.forcitis.one.organization.workforce.application.OrgStructureCommandDueJob;
import com.forcitis.one.organization.workforce.application.PersonnelCommandDueJob;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Card #1478, profile local only: the daily jobs run on a real-time cron, so on a pretend date nothing would fall due. When the settable clock is set (LocalClockController), they run once for the new date,
 * in the order of their daily schedule (personnel commands 00:05, org-structure commands 00:07, countersign deadlines 00:10), by the very method the scheduler calls.
 * The production schedule is unchanged.
 */
@Component
@Profile("local")
class WorkforceLocalDueJobs implements LocalDueJobs {

    private final PersonnelCommandDueJob personnel;
    private final OrgStructureCommandDueJob orgStructure;
    private final AttributionCountersignDeadlineJob countersign;

    WorkforceLocalDueJobs(PersonnelCommandDueJob personnel, OrgStructureCommandDueJob orgStructure, AttributionCountersignDeadlineJob countersign) {
        this.personnel = personnel;
        this.orgStructure = orgStructure;
        this.countersign = countersign;
    }

    @Override
    public void runOnce() {
        personnel.run();
        orgStructure.run();
        countersign.run();
    }
}
