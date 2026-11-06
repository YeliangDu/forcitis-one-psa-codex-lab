package com.forcitis.one.organization.payroll.api;

import java.util.Objects;

/** Withdraws one unconsumed bonus input. It needs no approval of its own: it only takes back what an approval let in. */
public record VoidBonusInput(BonusInputId id) {

    public VoidBonusInput {
        Objects.requireNonNull(id, "id");
    }
}
