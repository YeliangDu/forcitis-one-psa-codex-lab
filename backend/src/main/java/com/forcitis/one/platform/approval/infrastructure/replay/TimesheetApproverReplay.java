package com.forcitis.one.platform.approval.infrastructure.replay;

import com.forcitis.one.kernel.EmployeeId;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares by "every old approver is in the new candidate set" and by routing flags (branches run in parallel, so order is not compared). */
final class TimesheetApproverReplay {

    private TimesheetApproverReplay() {
    }

    static ReplayReport replay(ReplayExport export) {
        ExportedSnapshot snapshot = new ExportedSnapshot(export.snapshot());
        java.util.Set<UserId> administrator = export.processAdministratorUserId() == null ? java.util.Set.of() : java.util.Set.of(ExportedAccounts.account(export.processAdministratorUserId()));
        AssigneeResolver resolver = new AssigneeResolver(snapshot, snapshot, administrator, new ExportedAccounts(),
                java.time.LocalDate.EPOCH); // an export carries no leave flag, so the day is irrelevant
        Set<Long> hardCoded = new HashSet<>(export.legacyFallbackUserIds());

        Map<ReplayVerdict, Integer> verdicts = new EnumMap<>(ReplayVerdict.class);
        Map<ResolutionKind, Integer> resolutions = new EnumMap<>(ResolutionKind.class);
        List<ReplayReport.Difference> differences = new ArrayList<>();
        int[] nodes = {0};
        for (ReplayExport.InstanceRecord instance : export.instances()) {
            ProcessVariables variables = new ProcessVariables(LegacyVocabulary.fields(instance.formFields()),
                    LegacyVocabulary.fields(instance.variables()));
            EmployeeId initiator = ExportedIds.employee(instance.initiatorId());
            Set<String> flags = new HashSet<>();
            instance.legacyHourTypes().forEach(type -> flags.add(LegacyVocabulary.flag(type)));
            Map<String, ReplayExport.NodeRecord> exported = new LinkedHashMap<>();
            for (ReplayExport.NodeRecord node : instance.nodes()) {
                if (!LegacyVocabulary.isReworkNode(node)) {
                    exported.put(LegacyVocabulary.nodeKey(node.nodeKey()), node);
                }
            }
            Set<String> nodeKeys = new LinkedHashSet<>();
            TimesheetBranches.ALL.stream().filter(b -> flags.contains(b.flag())).forEach(b -> nodeKeys.add(b.nodeKey()));
            nodeKeys.addAll(exported.keySet());
            for (String nodeKey : nodeKeys) {
                nodes[0]++;
                TimesheetBranches.Branch branch = TimesheetBranches.byNode(nodeKey);
                ReplayExport.NodeRecord node = exported.get(nodeKey);
                boolean expected = branch != null && flags.contains(branch.flag());
                List<Long> actual = node == null ? List.of() : node.actualApprovers();
                if (branch == null || !expected) {
                    record(verdicts, resolutions, differences, new ReplayReport.Difference(instance.instanceId(), nodeKey,
                            node.rule().get("type"), ReplayVerdict.DEFECT, null, List.of(), actual, "node not in the new process for these hour types"));
                    continue;
                }
                AssignmentRule rule = AssignmentRuleCodec.parse(branch.rule().get("type"), branch.rule()::get);
                String note = "";
                if (node != null) {
                    Map<String, String> oldRule = LegacyVocabulary.rule(node.rule());
                    if (!AssignmentRuleCodec.parse(oldRule.get("type"), oldRule::get).equals(rule)) {
                        note = "old rule differs from the new rule of the node";
                    }
                }
                Resolution resolution = resolver.resolve(rule, false, true, initiator, variables);
                Set<Long> candidates = new HashSet<>();
                resolution.candidates().forEach(c -> candidates.add(ExportedAccounts.person(c)));
                ReplayVerdict verdict;
                if (!note.isEmpty()) {
                    verdict = ReplayVerdict.DEFECT;
                } else if (node == null) {
                    verdict = resolution.kind() == ResolutionKind.SKIPPED ? ReplayVerdict.MATCH : ReplayVerdict.DEFECT;
                    note = verdict == ReplayVerdict.MATCH ? "" : "old skipped the node, the new process assigns it";
                } else {
                    verdict = verdictOf(node, instance.initiatorId(), resolution, candidates, snapshot, hardCoded);
                }
                resolutions.merge(resolution.kind(), 1, Integer::sum);
                verdicts.merge(verdict, 1, Integer::sum);
                if (verdict != ReplayVerdict.MATCH) {
                    differences.add(new ReplayReport.Difference(instance.instanceId(), nodeKey, branch.rule().get("type"), verdict,
                            resolution.kind(), candidates.stream().sorted().toList(), actual.stream().sorted().toList(), note));
                }
            }
        }
        return new ReplayReport(export.processKey(), export.instances().size(), nodes[0], verdicts, resolutions, differences);
    }

    private static void record(Map<ReplayVerdict, Integer> verdicts, Map<ResolutionKind, Integer> resolutions,
            List<ReplayReport.Difference> differences, ReplayReport.Difference difference) {
        verdicts.merge(difference.verdict(), 1, Integer::sum);
        differences.add(difference);
    }

    private static ReplayVerdict verdictOf(ReplayExport.NodeRecord node, long initiator, Resolution resolution,
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
        if (first == initiator && !node.selfApprovalAllowed()) {
            return ReplayVerdict.REVIEWED_SELF_APPROVAL;
        }
        return switch (resolution.kind()) {
            case ESCALATED_REPORTING_LINE -> ReplayVerdict.REVIEWED_REPORTING_LINE;
            case ESCALATED_SELF_APPROVAL -> ReplayVerdict.REVIEWED_SELF_APPROVAL;
            case ADMINISTRATOR_FALLBACK -> ReplayVerdict.REVIEWED_ADMINISTRATOR_FALLBACK;
            case RESOLVED, SKIPPED, UNRESOLVED, INVALID_INPUT -> ReplayVerdict.DEFECT;
        };
    }
}
