package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.ContractStatus.*;

import com.forcitis.one.kernel.TransitionTable;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractStatusMachineTest {
    /** Status holder: the table has no behaviour of its own yet (commands arrive in later cards), so the machine is exhausted through it. */
    static final class Holder {
        ContractStatus status;

        Holder(ContractStatus status) {
            this.status = status;
        }

        void to(ContractStatus target) {
            status = ContractStatus.MACHINE.require(status, target);
        }
    }

    @Test
    @DisplayName("#1438 every move to every status is tried from every status: only the signed edges succeed")
    void everyOperationInEveryStatus() {
        var table = new TransitionTable<ContractStatus, Holder>(ContractStatus.class, Holder::new, h -> h.status);
        table.operation("to DRAFT", h -> h.to(DRAFT), IN_APPROVAL, DRAFT);
        table.operation("to IN_APPROVAL", h -> h.to(IN_APPROVAL), DRAFT, IN_APPROVAL);
        table.operation("to APPROVED", h -> h.to(APPROVED), IN_APPROVAL, APPROVED);
        table.operation("to SIGNED", h -> h.to(SIGNED), APPROVED, SIGNED);
        table.operation("to TERMINATED", h -> h.to(TERMINATED), SIGNED, TERMINATED);
        table.operation("to VOID", h -> h.to(VOID), DRAFT, VOID, IN_APPROVAL, VOID, APPROVED, VOID, SIGNED, VOID);

        table.verify();
    }
}
