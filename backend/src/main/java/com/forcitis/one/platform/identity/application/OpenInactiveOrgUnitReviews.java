package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.WorkdayDeadlines;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.events.OrgUnitDissolved;
import com.forcitis.one.organization.workforce.events.OrgUnitMerged;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * An org unit was dissolved or merged away (RULE-387, ADR-0032 decision 4): every role whose current version lists it, enabled or not and with or without
 * holders, gets one scope review item about (role, unit), for the organization data administrator to handle. One item stays OPEN or UPHELD at a time per pair, so
 * a repeated delivery adds nothing. The unit a merge moved the people to is never written into the role. No recipient and no outbound message; one P3 alert
 * line per item opened carries the event, item, role, org unit and trigger command ids, no names.
 */
@SystemActorName("platform.identity.permission-adjustment")
@Component
class OpenInactiveOrgUnitReviews {

    private static final Logger LOG = LoggerFactory.getLogger(OpenInactiveOrgUnitReviews.class);
    private static final int ESCALATION_WORKDAYS = 5;
    private static final String ACTOR = OpenInactiveOrgUnitReviews.class.getAnnotation(SystemActorName.class).value();

    private final RoleRepository roles;
    private final PermissionAdjustmentRepository adjustments;
    private final WorkdayDeadlines deadlines;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    OpenInactiveOrgUnitReviews(RoleRepository roles, PermissionAdjustmentRepository adjustments, WorkdayDeadlines deadlines, DomainEvents events, IdGenerator ids,
            Clock clock, BusinessZone zone) {
        this.roles = roles;
        this.adjustments = adjustments;
        this.deadlines = deadlines;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    @ApplicationModuleListener
    void on(OrgUnitDissolved event) {
        open(event.envelope(), event.orgUnitId(), event.orgStructureCommandId());
    }

    @ApplicationModuleListener
    void on(OrgUnitMerged event) {
        open(event.envelope(), event.orgUnitId(), event.orgStructureCommandId());
    }

    private void open(EventEnvelope trigger, OrgUnitId orgUnit, OrgStructureCommandId command) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            for (RoleId role : roles.currentRolesWithUnit(orgUnit)) {
                PermissionAdjustmentId id = new PermissionAdjustmentId(ids.next());
                Instant now = clock.instant();
                PermissionAdjustment item = PermissionAdjustment.openForInactiveOrgUnit(id, role, orgUnit, command, escalationDueAt(), now);
                if (!adjustments.insertOpenForInactiveUnit(item)) {
                    continue;
                }
                events.publish(item);
                LOG.error("ALERT P3 platform.identity permission-adjustment source-2 opened; event {}; item {}; role {}; org unit {}; trigger command {}", trigger.eventId(),
                        id.value(), role.value(), orgUnit.value(), command.value());
            }
        }
    }

    /** The end of the fifth workday on the default work calendar: a dissolved unit has no legal entity of its own, so the entity is always null (one-design ruling on #1244) and the one alert line per item is the only one. */
    private Instant escalationDueAt() {
        return deadlines.endOfWorkdayAfter(zone.dateOf(clock.instant()), ESCALATION_WORKDAYS, null);
    }
}
