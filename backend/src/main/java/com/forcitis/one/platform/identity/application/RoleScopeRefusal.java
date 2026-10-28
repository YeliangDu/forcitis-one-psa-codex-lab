package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import java.util.List;

/**
 * The detail of a platform.identity.grant.refused entry for a scope a two-person role may not have (F-1 card C4): the reason code, the
 * scope kind and, for units with descendants, the ids of those units; no name, no permission code.
 */
final class RoleScopeRefusal {

    private static final int UNIT_TEXT_LIMIT = 1500;

    private RoleScopeRefusal() {
    }

    static String detail(String reason, Role role) {
        String text = "reason=" + reason + " scope=" + role.accessScope().name();
        if (SensitivePermissions.WITH_DESCENDANTS.equals(reason)) {
            List<CustomOrgUnit> below = role.customOrgUnits().stream().filter(CustomOrgUnit::includeDescendants).toList();
            return text + " (a two-person role lists its custom org units without descendants, ADR-0044 section 1) orgUnits="
                    + CustomOrgUnit.auditText(below, UNIT_TEXT_LIMIT);
        }
        return text + " (a two-person role takes ALL, CUSTOM_ORG_UNITS or SELF only, ADR-0032 section 3)";
    }
}
