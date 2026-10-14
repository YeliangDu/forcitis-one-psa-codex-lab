package com.forcitis.one.platform.identity.api;

public enum AccessScopeKind {
    ALL,
    /** Only the org units listed for the role. */
    CUSTOM_ORG_UNITS,
    OWN_ORG_UNIT,
    OWN_ORG_UNIT_SUBTREE,
    SELF,
    /** Indirect reports are not included (user decision). */
    SELF_AND_DIRECT_REPORTS
}
