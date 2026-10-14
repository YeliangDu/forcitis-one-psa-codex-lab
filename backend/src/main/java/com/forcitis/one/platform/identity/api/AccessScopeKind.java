package com.forcitis.one.platform.identity.api;

/**
 * How far a role sees business rows (RULE-349). New roles start at SELF (RULE-345). The scope a person actually has is the
 * union over their enabled roles, computed as an {@link AccessScope}.
 */
public enum AccessScopeKind {
    /** Unrestricted: sees every row. */
    ALL,
    /** Only the org units listed for the role (counts only for this kind, RULE-349). */
    CUSTOM_ORG_UNITS,
    /** The person's own org unit. */
    OWN_ORG_UNIT,
    /** The person's own org unit and every unit below it. */
    OWN_ORG_UNIT_SUBTREE,
    /** The person's own rows. */
    SELF,
    /** The person and the people who report to them directly; indirect reports are not included (RULE-349, user decision). */
    SELF_AND_DIRECT_REPORTS
}
