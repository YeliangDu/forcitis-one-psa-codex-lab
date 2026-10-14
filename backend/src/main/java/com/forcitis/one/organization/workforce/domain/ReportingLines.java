package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Validation of the person-level direct leader (RULE-372): the leader must exist, be employed, not be the person
 * themselves, and the chain must not loop. Invalid leaders are not written (empty reporting line) and reported to the
 * data stewards; valid ones, including an explicit "no leader" from the source, are synced as they are (RULE-365).
 */
final class ReportingLines {

    private ReportingLines() {
    }

    /**
     * @param claimed     employed people with the leader the source names (non-null leaders only)
     * @param statusOf    employment status of every person known after this run
     * @param storedLeader leader of people this run does not touch (their stored leader keeps chains continuous)
     * @param diffs       receives one ISSUE per rejected leader
     * @return the accepted leader per person
     */
    static Map<UserId, UserId> accept(Map<UserId, UserId> claimed, Map<UserId, EmploymentStatus> statusOf,
                                      Function<UserId, UserId> storedLeader, List<SyncDiff> diffs) {
        Map<UserId, UserId> candidates = new LinkedHashMap<>();
        claimed.forEach((person, leader) -> {
            String subject = "employee:" + person.value();
            if (person.equals(leader)) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.LEADER_IS_SELF, "direct leader is the person themselves"));
            } else if (!statusOf.containsKey(leader)) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.LEADER_NOT_FOUND, "direct leader " + leader.value() + " does not exist"));
            } else if (statusOf.get(leader) != EmploymentStatus.EMPLOYED) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.LEADER_SEPARATED, "direct leader " + leader.value() + " has separated"));
            } else {
                candidates.put(person, leader);
            }
        });
        Set<UserId> inCycle = findCycles(candidates, claimed.keySet(), storedLeader);
        Map<UserId, UserId> accepted = new LinkedHashMap<>();
        candidates.forEach((person, leader) -> {
            if (inCycle.contains(person)) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, "employee:" + person.value(), IssueKind.LEADER_CYCLE,
                        "direct leader chain of the person loops"));
            } else {
                accepted.put(person, leader);
            }
        });
        return accepted;
    }

    private static Set<UserId> findCycles(Map<UserId, UserId> candidates, Set<UserId> claimedPeople,
                                          Function<UserId, UserId> storedLeader) {
        Function<UserId, UserId> next = person -> {
            if (candidates.containsKey(person)) {
                return candidates.get(person);
            }
            return claimedPeople.contains(person) ? null : storedLeader.apply(person);
        };
        Map<UserId, Integer> state = new HashMap<>();
        Set<UserId> inCycle = new HashSet<>();
        for (UserId start : candidates.keySet()) {
            if (state.containsKey(start)) {
                continue;
            }
            List<UserId> path = new ArrayList<>();
            UserId at = start;
            while (at != null && !state.containsKey(at)) {
                state.put(at, 1);
                path.add(at);
                at = next.apply(at);
            }
            if (at != null && state.get(at) == 1) {
                inCycle.addAll(path.subList(path.indexOf(at), path.size()));
            }
            path.forEach(p -> state.put(p, 2));
        }
        inCycle.retainAll(candidates.keySet());
        return inCycle;
    }
}
