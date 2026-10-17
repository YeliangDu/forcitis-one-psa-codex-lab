package com.forcitis.one.platform.identity.api;

/** The closed list of rules the single operator mode relaxes (ADR-0030 section 2). A new value needs a new ADR. */
public enum SingleOperatorRelaxation {
    WITNESS_OMITTED,
    EMERGENCY_ACCOUNT_MINIMUM_ONE,
    SUPER_ADMIN_MINIMUM_ONE
}
