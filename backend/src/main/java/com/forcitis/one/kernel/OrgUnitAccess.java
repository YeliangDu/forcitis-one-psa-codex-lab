package com.forcitis.one.kernel;

import java.util.Set;
import java.util.UUID;

/**
 * Which org units the signed-in person may see through a permission, worked out from the scope of the enabled roles that grant it.
 * Implemented by whoever owns roles, asked by whoever serves org units, so the org module needs no dependency on it. When the scope
 * cannot be worked out the call throws: nothing is shown instead of everything (ADR-0026, RULE-350).
 */
public interface OrgUnitAccess {

    Visible visibleOrgUnits(String permissionCode);

    /** orgUnits are visible as such, orgUnitSubtrees with everything below them; empty and not everything means nothing. */
    record Visible(boolean everything, Set<UUID> orgUnits, Set<UUID> orgUnitSubtrees) {
        public Visible {
            orgUnits = Set.copyOf(orgUnits);
            orgUnitSubtrees = Set.copyOf(orgUnitSubtrees);
        }

        public static Visible all() {
            return new Visible(true, Set.of(), Set.of());
        }

        public static Visible nothing() {
            return new Visible(false, Set.of(), Set.of());
        }
    }
}
