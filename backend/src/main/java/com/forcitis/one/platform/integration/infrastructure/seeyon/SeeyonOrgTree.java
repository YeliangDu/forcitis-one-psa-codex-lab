package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.kernel.ExternalSourceBrokenException;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SeeyonOrgTree {

    private SeeyonOrgTree() {
    }

    static List<SourceOrgUnit> resolve(List<OrgUnitRow> rows, String rootPath) {
        List<OrgUnitRow> sorted = rows.stream()
                .sorted(Comparator.comparingInt((OrgUnitRow u) -> u.path().length())
                        .thenComparing(u -> u.sortOrder() == null ? Integer.MAX_VALUE : u.sortOrder())
                        .thenComparingLong(u -> u.unitId().value()))
                .toList();
        Map<String, OrgUnitRow> byPath = new LinkedHashMap<>();
        Map<SourceUnitId, SourceUnitId> sharedWith = new HashMap<>();
        for (OrgUnitRow unit : sorted) {
            OrgUnitRow existing = byPath.get(unit.path());
            if (existing == null) {
                byPath.put(unit.path(), unit);
                continue;
            }
            sharedWith.put(unit.unitId(), existing.unitId());
            if (existing.state() != SourceRecordState.ACTIVE && unit.state() == SourceRecordState.ACTIVE) {
                byPath.put(unit.path(), unit);
            }
        }
        return sorted.stream()
                .map(unit -> new SourceOrgUnit(unit.unitId(), unit.code(), unit.name(), parentOf(unit, byPath, rootPath), unit.sortOrder(),
                        unit.state(), unit.emptyFlags(), unit.regionKind(), unit.rawRegion(), unit.deliveryUnit(),
                        sharedWith.get(unit.unitId())))
                .toList();
    }

    private static SourceUnitId parentOf(OrgUnitRow unit, Map<String, OrgUnitRow> byPath, String root) {
        String path = unit.path();
                if (!path.startsWith(root) || (path.length() - root.length()) % SeeyonViewSql.PATH_LEVEL_LENGTH != 0) {
            throw new ExternalSourceBrokenException("org unit " + unit.unitId().value()
                    + " is not below the sync root of the organisation tree");
        }
        if (path.equals(root)) {
            return null;
        }
        OrgUnitRow parent = byPath.get(path.substring(0, path.length() - SeeyonViewSql.PATH_LEVEL_LENGTH));
        if (parent == null) {
            throw new ExternalSourceBrokenException("org unit " + unit.unitId().value()
                    + " has no parent in the organisation tree; the whole read is rejected");
        }
        return parent.unitId();
    }
}
