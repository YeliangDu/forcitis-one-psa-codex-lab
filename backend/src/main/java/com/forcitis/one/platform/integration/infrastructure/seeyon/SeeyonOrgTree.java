package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.kernel.ExternalSourceBrokenException;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SeeyonOrgTree {

    static final String DEPARTMENT = "Department";
    static final String ACCOUNT = "Account";

    private SeeyonOrgTree() {
    }

    static List<PlacedUnit> resolve(List<OrgUnitRow> rows, String rootPath) {
        checkTypes(rows, rootPath);
        List<OrgUnitRow> sorted = rows.stream()
                .sorted(Comparator.comparingInt((OrgUnitRow u) -> u.path().length())
                        .thenComparing(u -> u.sortOrder() == null ? Integer.MAX_VALUE : u.sortOrder())
                        .thenComparingLong(OrgUnitRow::unitId))
                .toList();
        Map<String, OrgUnitRow> byPath = new LinkedHashMap<>();
        Map<Long, Long> sharedWith = new HashMap<>();
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
        return sorted.stream().map(unit -> new PlacedUnit(unit, parentOf(unit, byPath, rootPath), sharedWith.get(unit.unitId()))).toList();
    }

    /**
     * C-3, C-4, RULING-R1B0-2: exact, case-sensitive match in Java (the column's collation ignores case); the root may be either value, every
     * other unit must be a Department. The message carries the column and counts only.
     */
    private static void checkTypes(List<OrgUnitRow> rows, String rootPath) {
        long illegal = rows.stream().filter(u -> !DEPARTMENT.equals(u.type()) && !ACCOUNT.equals(u.type())).count();
        if (illegal > 0) {
            throw new ExternalSourceBrokenException("org unit TYPE: " + illegal
                    + " unit(s) hold NULL or a value other than Department or Account; the whole read is rejected");
        }
        long accountsBelowRoot = rows.stream().filter(u -> ACCOUNT.equals(u.type()) && !u.path().equals(rootPath)).count();
        if (accountsBelowRoot > 0) {
            throw new ExternalSourceBrokenException("org unit TYPE: " + accountsBelowRoot
                    + " unit(s) below the sync root are Account; the whole read is rejected");
        }
    }

    private static Long parentOf(OrgUnitRow unit, Map<String, OrgUnitRow> byPath, String root) {
        String path = unit.path();
        if (!path.startsWith(root) || (path.length() - root.length()) % SeeyonViewSql.PATH_LEVEL_LENGTH != 0) {
            throw new ExternalSourceBrokenException("org unit " + unit.unitId() + " is not below the sync root of the organisation tree");
        }
        if (path.equals(root)) {
            return null;
        }
        OrgUnitRow parent = byPath.get(path.substring(0, path.length() - SeeyonViewSql.PATH_LEVEL_LENGTH));
        if (parent == null) {
            throw new ExternalSourceBrokenException("org unit " + unit.unitId() + " has no parent in the organisation tree; the whole read is rejected");
        }
        return parent.unitId();
    }
}
