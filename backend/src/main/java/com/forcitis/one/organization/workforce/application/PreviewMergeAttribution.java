package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.MergedPosts;
import com.forcitis.one.organization.workforce.api.OrgMergePreview;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommands;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PreviewOrgUnitMerge;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.MergeLine;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.Person;
import com.forcitis.one.organization.workforce.domain.CountersignReadQueries;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.OrgCommandResolutionSources;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The merge rehearsal with the cost legal entity of each person before and after; whether the merge would need the countersign for them. Reads nothing it does not show and writes nothing. */
@Service
public class PreviewMergeAttribution {
    private final CountersignReadQueries names;
    private final OrgStructureCommands structureCommands;
    private final LegalEntityResolutionSources sources;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    PreviewMergeAttribution(CountersignReadQueries names, OrgStructureCommands structureCommands, LegalEntityResolutionSources sources, CurrentUser currentUser, IdGenerator ids, Clock clock,
                            BusinessZone zone) {
        this.names = names;
        this.structureCommands = structureCommands;
        this.sources = sources;
        this.currentUser = currentUser;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    public List<MergeLine> execute(OrgUnitId unit, OrgUnitId mergeInto, LocalDate day) {
        OrgMergePreview preview = structureCommands.previewMerge(new PreviewOrgUnitMerge(unit, mergeInto, day));
        Instant now = clock.instant();
        OrgStructureCommand merge = OrgStructureCommand.enter(new OrgStructureCommandId(ids.next()), new EnterOrgStructureCommand(OrgStructureTargetKind.ORG_UNIT,
                OrgStructureCommandKind.MERGE, unit, null, day, null, null, null, null, null, null, null, null, mergeInto, null), ids.next(), currentUser.userId(), now, zone.dateOf(now));
        LegalEntityResolutionSources after = OrgCommandResolutionSources.after(sources, merge);
        Map<EmployeeId, CountersignReadQueries.Person> people = names.employees(preview.employees());
        List<Optional<LegalEntityId>> before = new ArrayList<>();
        List<Optional<LegalEntityId>> next = new ArrayList<>();
        Set<LegalEntityId> entityIds = new LinkedHashSet<>();
        for (MergedPosts held : preview.posts()) {
            // A concurrent post never decides the cost legal entity (amendment D): a person without their primary post in the unit has no change to show.
            EmployeeId employee = held.employee();
            Optional<LegalEntityId> from = held.primaryInUnit() ? LegalEntityResolution.resolve(sources, employee, day) : Optional.empty();
            Optional<LegalEntityId> to = held.primaryInUnit() ? LegalEntityResolution.resolve(after, employee, day) : Optional.empty();
            before.add(from);
            next.add(to);
            from.ifPresent(entityIds::add);
            to.ifPresent(entityIds::add);
        }
        Map<LegalEntityId, String> entities = names.legalEntityNames(entityIds);
        String unitName = names.orgUnitName(unit).orElse(null);
        List<MergeLine> lines = new ArrayList<>();
        for (int i = 0; i < preview.employees().size(); i++) {
            EmployeeId employee = preview.employees().get(i);
            CountersignReadQueries.Person person = people.get(employee);
            LegalEntityId from = before.get(i).orElse(null);
            LegalEntityId to = next.get(i).orElse(null);
            lines.add(new MergeLine(employee.value(), person == null ? "" : person.displayName(), person == null ? "" : person.employeeCode(), unit.value(), unitName,
                    from == null ? null : from.value(), from == null ? null : entities.get(from), to == null ? null : to.value(), to == null ? null : entities.get(to),
                    from != null && to != null && !from.equals(to), preview.posts().get(i).primaryInUnit(), preview.posts().get(i).concurrentAssignmentCount()));
        }
        return lines;
    }
}
