package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tree questions over the snapshot's org units. Cycle-safe: a corrupt parent chain never loops. */
public final class OrgHierarchy {

    private OrgHierarchy() {
    }

    /** Ancestors, nearest first, excluding the unit itself; revoked ancestors are still part of the structure. */
    public static List<OrgUnitId> ancestors(Map<OrgUnitId, OrgUnit> unitsById, OrgUnitId orgUnitId) {
        List<OrgUnitId> chain = new ArrayList<>();
        Set<OrgUnitId> seen = new HashSet<>();
        seen.add(orgUnitId);
        OrgUnit current = unitsById.get(orgUnitId);
        while (current != null && current.parentOrgUnitId() != null && seen.add(current.parentOrgUnitId())) {
            chain.add(current.parentOrgUnitId());
            current = unitsById.get(current.parentOrgUnitId());
        }
        return chain;
    }

    /**
     * "Org unit and below" for scope expansion (RULE-360): the unit and its descendants, parents first, without any
     * revoked unit and without anything below a revoked unit. Disabled units are kept: only REVOKED means dissolved.
     */
    public static List<OrgUnitId> unitAndBelow(Collection<OrgUnit> units, OrgUnitId root) {
        Map<OrgUnitId, List<OrgUnit>> childrenOf = new LinkedHashMap<>();
        Map<OrgUnitId, OrgUnit> byId = new LinkedHashMap<>();
        for (OrgUnit unit : units) {
            byId.put(unit.orgUnitId(), unit);
            if (unit.parentOrgUnitId() != null) {
                childrenOf.computeIfAbsent(unit.parentOrgUnitId(), k -> new ArrayList<>()).add(unit);
            }
        }
        OrgUnit start = byId.get(root);
        if (start == null || start.status() == OrgUnitStatus.REVOKED) {
            return List.of();
        }
        List<OrgUnitId> result = new ArrayList<>();
        Set<OrgUnitId> seen = new HashSet<>();
        Deque<OrgUnit> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.orgUnitId());
        while (!queue.isEmpty()) {
            OrgUnit unit = queue.poll();
            result.add(unit.orgUnitId());
            for (OrgUnit child : childrenOf.getOrDefault(unit.orgUnitId(), List.of())) {
                if (child.status() != OrgUnitStatus.REVOKED && seen.add(child.orgUnitId())) {
                    queue.add(child);
                }
            }
        }
        return result;
    }
}
