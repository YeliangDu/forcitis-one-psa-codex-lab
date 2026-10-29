package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Invalid leaders are not written and are reported; the chain must not loop. */
final class ReportingLines {
    private ReportingLines() {
    }

    static Map<EmployeeId, EmployeeId> accept(Map<EmployeeId, EmployeeId> claimed, Map<EmployeeId, EmploymentStatus> statusOf,
                                      Function<EmployeeId, @Nullable EmployeeId> storedSupervisor, List<SyncDiff> diffs) {
        Map<EmployeeId, EmployeeId> candidates = new LinkedHashMap<>();
        claimed.forEach((person, leader) -> {
            String subject = "employee:" + person.value();
            if (person.equals(leader)) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.SUPERVISOR_IS_SELF, "direct leader is the person themselves"));
            } else if (!statusOf.containsKey(leader)) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.SUPERVISOR_NOT_FOUND, "direct leader " + leader.value() + " does not exist"));
            } else if (statusOf.get(leader) != EmploymentStatus.EMPLOYED) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.SUPERVISOR_SEPARATED, "direct leader " + leader.value() + " has separated"));
            } else {
                candidates.put(person, leader);
            }
        });
        Set<EmployeeId> inCycle = findCycles(candidates, claimed.keySet(), storedSupervisor);
        Map<EmployeeId, EmployeeId> accepted = new LinkedHashMap<>();
        candidates.forEach((person, leader) -> {
            if (inCycle.contains(person)) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, "employee:" + person.value(), IssueKind.SUPERVISOR_CYCLE,
                        "direct leader chain of the person loops"));
            } else {
                accepted.put(person, leader);
            }
        });
        return accepted;
    }

    private static Set<EmployeeId> findCycles(Map<EmployeeId, EmployeeId> candidates, Set<EmployeeId> claimedPeople,
                                          Function<EmployeeId, @Nullable EmployeeId> storedSupervisor) {
        Function<EmployeeId, @Nullable EmployeeId> next = person -> {
            if (candidates.containsKey(person)) {
                return candidates.get(person);
            }
            return claimedPeople.contains(person) ? null : storedSupervisor.apply(person);
        };
        Map<EmployeeId, Integer> state = new HashMap<>();
        Set<EmployeeId> inCycle = new HashSet<>();
        for (EmployeeId start : candidates.keySet()) {
            if (state.containsKey(start)) {
                continue;
            }
            List<EmployeeId> path = new ArrayList<>();
            @Nullable EmployeeId at = start;
            while (at != null && !state.containsKey(at)) {
                state.put(at, 1);
                path.add(at);
                at = next.apply(at);
            }
            if (at != null && Objects.requireNonNull(state.get(at), "at is a state key: the loop above ends on a null or an already-visited person") == 1) {
                inCycle.addAll(path.subList(path.indexOf(at), path.size()));
            }
            path.forEach(p -> state.put(p, 2));
        }
        inCycle.retainAll(candidates.keySet());
        return inCycle;
    }
}
