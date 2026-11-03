package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AssignmentKind;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.HeldPost;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three transactions of a merge run: its start, one post's move, its finish. Each takes the root row of the merged org unit first and the employee
 * row after it (lock order: org unit root, then employee root), so a concurrent last approval and an execution serialize on the unit. The post's move
 * is a transfer entered through the personnel command service, the core-employment partition's own entry (DD amendment C of #907).
 */
@Component
class OrgMergeSteps {

    private final OrgStructureCommandRepository commands;
    private final OrgStructureRules rules;
    private final OrgMergeProgress progress;
    private final OrgMergeCompletion completion;
    private final PersonnelCommandService personnel;
    private final AttributionCountersignRepository countersigns;
    private final CountersignRequests requests;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    OrgMergeSteps(OrgStructureCommandRepository commands, OrgStructureRules rules, OrgMergeProgress progress, OrgMergeCompletion completion, PersonnelCommandService personnel,
                  AttributionCountersignRepository countersigns, CountersignRequests requests, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.rules = rules;
        this.progress = progress;
        this.completion = completion;
        this.personnel = personnel;
        this.countersigns = countersigns;
        this.requests = requests;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    /** Starts a pending merge that is due (the people of that moment are counted once), or finds which posts are left of one in progress; a finished merge has none left. */
    @Transactional
    public List<HeldPost> start(OrgStructureCommandId id, UUID actor) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        commands.lockOrgTree();
        OrgStructureCommand merge = lockedMerge(id);
        if (merge.status() == OrgCommandStatus.EFFECTIVE) {
            return List.of();
        }
        if (merge.status() == OrgCommandStatus.WITHDRAWN) {
            throw notAllowed("the merge was withdrawn");
        }
        if (merge.status() == OrgCommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN) {
            throw notAllowed("the merge awaits the legal entity countersign of its people's change");
        }
        List<HeldPost> posts = commands.postsIn(merge.targetOrgUnitId(), merge.effectiveDate());
        if (merge.status() == OrgCommandStatus.PENDING) {
            if (merge.effectiveDate().isAfter(today)) {
                throw notAllowed("the merge is due on " + merge.effectiveDate());
            }
            rules.checkMergeStillPossible(merge);
            long loaded = merge.version();
            String before = merge.status().name();
            merge.startMerge(employeesOf(posts));
            commands.update(merge, now, loaded);
            commands.insertTransition(merge.id(), loaded + 1, before, merge.status().name(), ActorType.SYSTEM, actor, today, now, correlation());
            // The merge has started, so a withdrawal request still open can no longer be decided (OrgCountersigns.requireDecidable): it lapses.
            requests.lapse(CountersignRequests.Parent.of(merge), countersigns.lockOfOrgCommand(merge.id()), ActorType.SYSTEM, actor, now, today, correlation());
        }
        return posts;
    }

    /** The merge counts people, not posts: one who holds a primary and a concurrent post in the unit counts once. */
    static int employeesOf(List<HeldPost> posts) {
        return (int) posts.stream().map(HeldPost::employee).distinct().count();
    }

    /**
     * Enters the transfer that moves one post into the receiving unit, unless the merge has entered one for it already (a post whose transfer awaits or lost its legal entity
     * countersign stays where it is until another command moves it). A post that something else has moved out since the list was made needs none. The employee is locked
     * before the check, so a command that holds the person has committed when the check reads. A primary post is named by no assignment: the transfer acts on the primary root.
     */
    @Transactional
    public void move(OrgStructureCommandId id, HeldPost post, UUID actor) {
        OrgStructureCommand merge = lockedMerge(id);
        if (merge.status() != OrgCommandStatus.IN_PROGRESS) {
            return;
        }
        commands.lockEmployee(post.employee());
        if (commands.postsIn(merge.targetOrgUnitId(), merge.effectiveDate()).contains(post) && !commands.hasGeneratedTransfer(merge.id(), post.assignment())) {
            personnel.enter(new EnterPersonnelCommand(post.employee(), PersonnelCommandKind.TRANSFER, merge.effectiveDate(), null, merge.mergeIntoOrgUnitId(), null, null, null, null,
                    null, null, null, null, null, post.kind() == AssignmentKind.CONCURRENT ? post.assignment() : null), new UserId(actor), merge.id());
        }
    }

    /** Counts who is out of the merged unit now, however they got out; a merge whose unit is empty ends here. */
    @Transactional
    public void settle(OrgStructureCommandId id) {
        OrgStructureCommand merge = lockedMerge(id);
        if (merge.status() == OrgCommandStatus.IN_PROGRESS) {
            progress.recount(merge);
        }
    }

    /** Ends the merge once nobody is left in the merged unit; it is refused while somebody is. */
    @Transactional
    public void finish(OrgStructureCommandId id, UUID actor) {
        OrgStructureCommand merge = lockedMerge(id);
        if (merge.status() != OrgCommandStatus.EFFECTIVE) {
            completion.finish(merge, actor);
        }
    }

    private OrgStructureCommand lockedMerge(OrgStructureCommandId id) {
        OrgStructureCommand found = commands.find(id).orElseThrow(OrgMergeSteps::notFound);
        if (found.kind() != OrgStructureCommandKind.MERGE) {
            throw notAllowed("the command is not a merge");
        }
        commands.lockOrgUnit(found.targetOrgUnitId());
        return commands.find(id).orElseThrow(OrgMergeSteps::notFound);
    }

    private UUID correlation() {
        return WorkContext.correlationId().orElseGet(ids::next);
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such org-structure command");
    }

    private static DomainException notAllowed(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT, message);
    }
}
