package com.forcitis.one.platform.approval.infrastructure.replay;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.domain.AssigneeResolver;
import com.forcitis.one.platform.approval.domain.AssignmentRule;
import com.forcitis.one.platform.approval.domain.AssignmentRuleCodec;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.Resolution;
import com.forcitis.one.platform.approval.domain.ResolutionKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replays recorded approver sequences on the new engine's AssigneeResolver (REIMAGINED_ARCHITECTURE §3.5 (a)). For every node
 * of every exported instance it computes the candidate set from the OLD organisation snapshot and compares by
 * "every old approver is in the new candidate set" (RULE-249 turned a random pick into a candidate group). Mismatches are
 * classified: reviewed corrections are accepted by category, everything else is a DEFECT.
 */
final class TimesheetApproverReplay {

    private TimesheetApproverReplay() {
    }

    static ReplayReport replay(ReplayExport export) {
        ExportedSnapshot snapshot = new ExportedSnapshot(export.snapshot());
        UserId administrator = export.processAdministratorUserId() == null ? null : new UserId(export.processAdministratorUserId());
        AssigneeResolver resolver = new AssigneeResolver(snapshot, snapshot, administrator);
        Set<Long> hardCoded = new HashSet<>(export.legacyFallbackUserIds());

        Map<ReplayVerdict, Integer> verdicts = new EnumMap<>(ReplayVerdict.class);
        Map<ResolutionKind, Integer> resolutions = new EnumMap<>(ResolutionKind.class);
        List<ReplayReport.Difference> differences = new ArrayList<>();
        int nodes = 0;
        for (ReplayExport.InstanceRecord instance : export.instances()) {
            ProcessVariables variables = new ProcessVariables(instance.formFields(), instance.variables());
            for (ReplayExport.NodeRecord node : instance.nodes()) {
                nodes++;
                AssignmentRule rule = AssignmentRuleCodec.parse(node.rule().get("type"), node.rule()::get);
                Resolution resolution = resolver.resolve(rule, node.selfApprovalAllowed(), new UserId(instance.initiatorId()), variables);
                resolutions.merge(resolution.kind(), 1, Integer::sum);
                Set<Long> candidates = new HashSet<>();
                resolution.candidates().forEach(c -> candidates.add(c.value()));
                ReplayVerdict verdict = verdictOf(node, instance.initiatorId(), rule, resolution, candidates, snapshot, hardCoded);
                verdicts.merge(verdict, 1, Integer::sum);
                if (verdict != ReplayVerdict.MATCH) {
                    differences.add(new ReplayReport.Difference(instance.instanceId(), node.nodeKey(), node.rule().get("type"), verdict,
                            resolution.kind(), candidates.stream().sorted().toList(), node.actualApprovers().stream().sorted().toList()));
                }
            }
        }
        return new ReplayReport(export.processKey(), export.instances().size(), nodes, verdicts, resolutions, differences);
    }

    private static ReplayVerdict verdictOf(ReplayExport.NodeRecord node, long initiator, AssignmentRule rule, Resolution resolution,
            Set<Long> candidates, ExportedSnapshot snapshot, Set<Long> hardCoded) {
        List<Long> unmatched = node.actualApprovers().stream().filter(a -> !candidates.contains(a)).toList();
        if (unmatched.isEmpty()) {
            return ReplayVerdict.MATCH;
        }
        Long first = unmatched.getFirst();
        if (hardCoded.contains(first)) {
            return ReplayVerdict.REVIEWED_HARDCODED_FALLBACK;
        }
        if (!snapshot.employed(first)) {
            return ReplayVerdict.REVIEWED_DISABLED_APPROVER;
        }
        if (first == initiator && !(rule instanceof AssignmentRule.Initiator) && !node.selfApprovalAllowed()) {
            return ReplayVerdict.REVIEWED_SELF_APPROVAL;
        }
        return switch (resolution.kind()) {
            case ESCALATED_REPORTING_LINE -> ReplayVerdict.REVIEWED_REPORTING_LINE;
            case ESCALATED_SELF_APPROVAL -> ReplayVerdict.REVIEWED_SELF_APPROVAL;
            case ADMINISTRATOR_FALLBACK -> ReplayVerdict.REVIEWED_ADMINISTRATOR_FALLBACK;
            case RESOLVED, UNRESOLVED -> ReplayVerdict.DEFECT;
        };
    }
}
