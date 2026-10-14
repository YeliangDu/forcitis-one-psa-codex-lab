package com.forcitis.one.platform.approval.infrastructure.replay;

import com.forcitis.one.platform.approval.domain.ResolutionKind;
import java.util.List;
import java.util.Map;

/** Outcome of a replay: counts per verdict and the details of every node that is not a plain MATCH. Ids only, no names. */
record ReplayReport(String processKey, int instances, int nodes, Map<ReplayVerdict, Integer> verdictCounts,
                    Map<ResolutionKind, Integer> resolutionCounts, List<Difference> differences) {

    ReplayReport {
        verdictCounts = Map.copyOf(verdictCounts);
        resolutionCounts = Map.copyOf(resolutionCounts);
        differences = List.copyOf(differences);
    }

    /** One node whose old approvers are not all in the new candidate set. */
    record Difference(String instanceId, String nodeKey, String ruleType, ReplayVerdict verdict, ResolutionKind resolution,
                      List<Long> candidates, List<Long> actualApprovers, String note) {
    }

    int count(ReplayVerdict verdict) {
        return verdictCounts.getOrDefault(verdict, 0);
    }

    /** The gate of the exit condition: no unexplained difference. */
    boolean hasDefects() {
        return count(ReplayVerdict.DEFECT) > 0;
    }

    String toMarkdown() {
        StringBuilder md = new StringBuilder("# TIMESHEET approver replay (step a: engine equivalence)\n\n");
        md.append("Process ").append(processKey).append(": ").append(instances).append(" instances, ").append(nodes).append(" nodes.\n\n");
        md.append("| verdict | nodes |\n|---|---|\n");
        for (ReplayVerdict verdict : ReplayVerdict.values()) {
            md.append("| ").append(verdict).append(" | ").append(count(verdict)).append(" |\n");
        }
        md.append("\nHow the new candidate sets came about:\n\n| resolution | nodes |\n|---|---|\n");
        for (ResolutionKind kind : ResolutionKind.values()) {
            md.append("| ").append(kind).append(" | ").append(resolutionCounts.getOrDefault(kind, 0)).append(" |\n");
        }
        md.append("\n## Differences\n\n");
        if (differences.isEmpty()) {
            md.append("None.\n");
        } else {
            md.append("| instance | node | rule | verdict | resolution | new candidates | old approvers | note |\n|---|---|---|---|---|---|---|---|\n");
            for (Difference d : differences) {
                md.append("| ").append(d.instanceId()).append(" | ").append(d.nodeKey()).append(" | ").append(d.ruleType())
                        .append(" | ").append(d.verdict()).append(" | ").append(d.resolution()).append(" | ")
                        .append(d.candidates()).append(" | ").append(d.actualApprovers()).append(" | ").append(d.note()).append(" |\n");
            }
        }
        return md.toString();
    }
}
