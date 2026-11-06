package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;

/** The legal edges of a labour contract, as tg_employment_contracts_change allows them (data design record WC0). "In force" is a reading of the dates, not a status. */
public enum ContractStatus {
    DRAFT,
    IN_APPROVAL,
    APPROVED,
    SIGNED,
    TERMINATED,
    VOID;

    public static final StateMachine<ContractStatus> MACHINE = StateMachine.of(ContractStatus.class)
            .allow(DRAFT, IN_APPROVAL, VOID)
            .allow(IN_APPROVAL, APPROVED, DRAFT, VOID)
            .allow(APPROVED, SIGNED, VOID)
            .allow(SIGNED, TERMINATED, VOID)
            .build();
}
