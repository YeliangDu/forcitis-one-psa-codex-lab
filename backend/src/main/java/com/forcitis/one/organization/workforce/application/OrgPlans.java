package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.OrgEffects;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgTimeline;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionPlanner;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Plans what an org-structure command does to the version table of its target, and what taking it back does; the plans are only values, the use case writes them. */
@Component
class OrgPlans {
    private final IdGenerator ids;

    OrgPlans(IdGenerator ids) {
        this.ids = ids;
    }

    VersionPlan forEntry(OrgStructureCommand command, OrgTimeline timeline, AuditEntryId auditEntryId) {
        return plan(command, timeline, values(command), auditEntryId);
    }

    /** The revocation of a merged unit, written when the last person has moved. */
    VersionPlan forMergeCompletion(OrgStructureCommand merge, OrgTimeline timeline, AuditEntryId auditEntryId) {
        return plan(merge, timeline, OrgEffects.mergeValues(), auditEntryId);
    }

    VersionPlan forWithdrawal(OrgStructureCommand command, OrgTimeline timeline) {
        Map<String, Object> values = values(command);
        if (values.isEmpty()) {
            return VersionPlan.empty();
        }
        return VersionPlanner.revert(timeline.current(), timeline::row, source -> source.isOrgCommand(command.id()), command.effectiveDate(), values.keySet(), row -> true,
                setters(timeline, columns(command)), ids::next);
    }

    private VersionPlan plan(OrgStructureCommand command, OrgTimeline timeline, Map<String, Object> values, AuditEntryId auditEntryId) {
        if (values.isEmpty()) {
            return VersionPlan.empty();
        }
        return VersionPlanner.apply(timeline.current(), new VersionPlanner.Change(command.effectiveDate(), values, row -> true, OrgEffects.coversEmptyDay(command)),
                ChangeSource.ofOrgCommand(command.id(), auditEntryId), setters(timeline, columns(command)), ids::next);
    }

    private static Map<String, Object> values(OrgStructureCommand command) {
        return command.targetPositionId() != null ? OrgEffects.positionValues(command) : OrgEffects.unitValues(command);
    }

    private static Set<String> columns(OrgStructureCommand command) {
        return command.targetPositionId() != null ? OrgEffects.POSITION_COLUMNS : OrgEffects.UNIT_COLUMNS;
    }

    /** The columns the row's own command set: everything for a migrated row, what the command names for a command row (a merge's revocation names the status). */
    private static Function<VersionRow, Set<String>> setters(OrgTimeline timeline, Set<String> all) {
        return row -> {
            if (!row.source().command()) {
                return all;
            }
            return OrgEffects.columnsOf(timeline.lookup().get(row.source().orgCommandId().value()));
        };
    }
}
