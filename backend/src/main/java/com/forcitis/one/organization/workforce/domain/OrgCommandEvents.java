package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.PendingEvent;
import com.forcitis.one.organization.workforce.events.OrgUnitAttributesSet;
import com.forcitis.one.organization.workforce.events.OrgUnitCreated;
import com.forcitis.one.organization.workforce.events.OrgUnitDissolved;
import com.forcitis.one.organization.workforce.events.OrgUnitHeadAssigned;
import com.forcitis.one.organization.workforce.events.OrgUnitMerged;
import com.forcitis.one.organization.workforce.events.OrgUnitReceivablesManagerAssigned;
import com.forcitis.one.organization.workforce.events.OrgUnitReparented;
import com.forcitis.one.organization.workforce.events.PositionCreated;
import com.forcitis.one.organization.workforce.events.PositionDisabled;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The event of each kind of org-structure command that took effect (ADR-0068 item 4); the envelope is about the command, dated by the effective date. */
final class OrgCommandEvents {

    private OrgCommandEvents() {
    }

    static Optional<PendingEvent> of(OrgStructureCommand c) {
        return Optional.ofNullable(switch (c.kind()) {
            case CREATE -> c.targetPositionId() != null
                    ? (PendingEvent) (e, s) -> new PositionCreated(e.wrap(draft("PositionCreated", c, s)), c.id(), c.targetPositionId(), c.effectiveDate())
                    : (PendingEvent) (e, s) -> new OrgUnitCreated(e.wrap(draft("OrgUnitCreated", c, s)), c.id(), Objects.requireNonNull(c.targetOrgUnitId()),
                            c.targetParentOrgUnitId(), c.effectiveDate());
            case DISABLE -> (e, s) -> new PositionDisabled(e.wrap(draft("PositionDisabled", c, s)), c.id(), Objects.requireNonNull(c.targetPositionId()), c.effectiveDate());
            case DISSOLVE -> (e, s) -> new OrgUnitDissolved(e.wrap(draft("OrgUnitDissolved", c, s)), c.id(), Objects.requireNonNull(c.targetOrgUnitId()), c.effectiveDate());
            case SET_ATTRIBUTES -> (e, s) -> new OrgUnitAttributesSet(e.wrap(draft("OrgUnitAttributesSet", c, s)), c.id(), Objects.requireNonNull(c.targetOrgUnitId()),
                    c.effectiveDate());
            case MERGE -> (e, s) -> new OrgUnitMerged(e.wrap(draft("OrgUnitMerged", c, s)), c.id(), Objects.requireNonNull(c.targetOrgUnitId()),
                    Objects.requireNonNull(c.mergeIntoOrgUnitId()), c.effectiveDate());
            case REPARENT -> (e, s) -> new OrgUnitReparented(e.wrap(draft("OrgUnitReparented", c, s)), c.id(), Objects.requireNonNull(c.targetOrgUnitId()),
                    Objects.requireNonNull(c.targetParentOrgUnitId()), c.effectiveDate());
            case ASSIGN_LEGAL_ENTITY -> null;
            case ASSIGN_HEAD -> (e, s) -> new OrgUnitHeadAssigned(e.wrap(draft("OrgUnitHeadAssigned", c, s)), c.id(), Objects.requireNonNull(c.targetOrgUnitId()),
                    Objects.requireNonNull(c.targetHeadEmployeeId()), c.effectiveDate());
            case ASSIGN_RECEIVABLES_MANAGER -> (e, s) -> new OrgUnitReceivablesManagerAssigned(e.wrap(draft("OrgUnitReceivablesManagerAssigned", c, s)), c.id(),
                    Objects.requireNonNull(c.targetOrgUnitId()), Objects.requireNonNull(c.targetReceivablesManagerEmployeeId()), c.effectiveDate());
        });
    }

    private static EventDraft draft(String name, OrgStructureCommand c, long sequence) {
        return new EventDraft("organization.workforce." + name, 1, "OrgStructureCommand", c.id().value(), sequence, null, c.effectiveDate(), List.of());
    }
}
