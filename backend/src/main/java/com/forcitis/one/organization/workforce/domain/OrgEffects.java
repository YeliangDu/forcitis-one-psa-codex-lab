package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What each kind of org-structure command sets on the version table of its target (ADR-0068 item 4, ADR-0073 item 2, W2 design section 3.3), by column
 * name. A column a command does not name keeps whatever the timeline holds. A merge sets nothing at entry; it revokes the merged unit when the last
 * person has moved.
 */
public final class OrgEffects {
    public static final String CODE = "code";
    public static final String NAME = "name";
    public static final String SHORT_NAME = "short_name";
    public static final String REGION = "region_kind";
    public static final String DELIVERY = "is_delivery_unit";
    public static final String PARENT = "parent_org_unit_id";
    public static final String HEAD = "head_employee_id";
    public static final String RECEIVABLES_MANAGER = "regional_receivables_manager_id";
    public static final String LIFECYCLE = "status";
    public static final Set<String> UNIT_COLUMNS = Set.of(CODE, NAME, SHORT_NAME, REGION, DELIVERY, PARENT, HEAD, RECEIVABLES_MANAGER, LIFECYCLE);
    public static final Set<String> POSITION_COLUMNS = Set.of(CODE, NAME, LIFECYCLE);

    private OrgEffects() {
    }

    /** The org unit version columns the command sets; a value may be null (a new unit has no head). A merge's revocation is {@link #mergeValues}. */
    public static Map<String, Object> unitValues(OrgStructureCommand c) {
        Map<String, Object> v = new LinkedHashMap<>();
        switch (c.kind()) {
            case CREATE -> {
                v.put(CODE, c.targetCode());
                v.put(NAME, c.targetName());
                v.put(SHORT_NAME, c.targetShortName());
                v.put(REGION, c.targetRegionKind() == null ? null : c.targetRegionKind().name());
                v.put(DELIVERY, c.targetIsDeliveryUnit());
                v.put(PARENT, c.targetParentOrgUnitId() == null ? null : c.targetParentOrgUnitId().value());
                v.put(HEAD, null);
                v.put(RECEIVABLES_MANAGER, null);
                v.put(LIFECYCLE, "ACTIVE");
            }
            case SET_ATTRIBUTES -> {
                putIfGiven(v, NAME, c.targetName());
                putIfGiven(v, SHORT_NAME, c.targetShortName());
                putIfGiven(v, REGION, c.targetRegionKind() == null ? null : c.targetRegionKind().name());
                putIfGiven(v, DELIVERY, c.targetIsDeliveryUnit());
            }
            case REPARENT -> v.put(PARENT, Objects.requireNonNull(c.targetParentOrgUnitId()).value());
            case ASSIGN_HEAD -> v.put(HEAD, Objects.requireNonNull(c.targetHeadEmployeeId()).value());
            case ASSIGN_RECEIVABLES_MANAGER -> v.put(RECEIVABLES_MANAGER, Objects.requireNonNull(c.targetReceivablesManagerEmployeeId()).value());
            case DISSOLVE -> v.put(LIFECYCLE, "REVOKED");
            case MERGE, DISABLE, ASSIGN_LEGAL_ENTITY -> {
            }
        }
        return v;
    }

    /** The revocation a completed merge writes on the merged unit, from the merge's effective date. */
    public static Map<String, Object> mergeValues() {
        return Map.of(LIFECYCLE, "REVOKED");
    }

    /** The position version columns the command sets. */
    public static Map<String, Object> positionValues(OrgStructureCommand c) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (c.kind() == OrgStructureCommandKind.CREATE) {
            v.put(CODE, c.targetCode());
            v.put(NAME, c.targetName());
            v.put(LIFECYCLE, "ACTIVE");
        } else if (c.kind() == OrgStructureCommandKind.DISABLE) {
            v.put(LIFECYCLE, "DISABLED");
        }
        return v;
    }

    /** The columns the command's own rows set: what its values name; a merge sets only the status of the merged unit. */
    public static Set<String> columnsOf(OrgStructureCommand c) {
        if (c.kind() == OrgStructureCommandKind.MERGE) {
            return mergeValues().keySet();
        }
        return c.targetPositionId() != null ? positionValues(c).keySet() : unitValues(c).keySet();
    }

    /** A create starts rows on a day that no current row covers. */
    public static boolean coversEmptyDay(OrgStructureCommand c) {
        return c.kind() == OrgStructureCommandKind.CREATE;
    }

    private static void putIfGiven(Map<String, Object> values, String column, @Nullable Object value) {
        if (value != null) {
            values.put(column, value);
        }
    }
}
