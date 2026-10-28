package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One org unit a CUSTOM_ORG_UNITS role version lists (ADR-0026 "1a 交付范围", ADR-0034 section 5): the unit alone, or the unit with every
 * unit below it. The org unit is a reference across schemas, held as its id only; whether it still exists is not checked when the role is
 * saved, a dangling one is caught by the scope review (F-1 card, section on references).
 */
@ValueObject
public record CustomOrgUnit(OrgUnitId orgUnitId, boolean includeDescendants) {

    public static final Comparator<CustomOrgUnit> BY_ORG_UNIT = Comparator.comparing(unit -> unit.orgUnitId().value());

    public CustomOrgUnit {
        Objects.requireNonNull(orgUnitId, "orgUnitId");
    }

    public static CustomOrgUnit only(OrgUnitId orgUnitId) {
        return new CustomOrgUnit(orgUnitId, false);
    }

    /**
     * The units as audit text (ids and the choice only, F-1 card C4): "none", or the ids in order, each followed by "+descendants" or
     * "+only", comma separated. An audit text holds at most 2000 characters, so past {@code limit} characters the rest is counted, not listed.
     */
    public static String auditText(Collection<CustomOrgUnit> units, int limit) {
        if (units.isEmpty()) {
            return "none";
        }
        List<CustomOrgUnit> ordered = units.stream().sorted(BY_ORG_UNIT).toList();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < ordered.size(); i++) {
            String one = ordered.get(i).orgUnitId().value() + (ordered.get(i).includeDescendants() ? "+descendants" : "+only");
            if (text.length() + one.length() + 40 > limit) {
                return text.append(" and ").append(ordered.size() - i).append(" more unit(s)").toString();
            }
            text.append(i == 0 ? "" : ",").append(one);
        }
        return text.toString();
    }
}
