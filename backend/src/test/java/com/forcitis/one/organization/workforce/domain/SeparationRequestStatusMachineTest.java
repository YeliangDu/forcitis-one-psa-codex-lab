package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.SeparationRequestStatus.*;

import com.forcitis.one.kernel.TransitionTable;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeparationRequestStatusMachineTest {
    /** Status holder: the table has no behaviour of its own yet (commands arrive in later cards), so the machine is exhausted through it. */
    static final class Holder {
        SeparationRequestStatus status;

        Holder(SeparationRequestStatus status) {
            this.status = status;
        }

        void to(SeparationRequestStatus target) {
            status = SeparationRequestStatus.MACHINE.require(status, target);
        }
    }

    @Test
    @DisplayName("#1438 every move to every status is tried from every status: only the signed edges succeed")
    void everyOperationInEveryStatus() {
        var table = new TransitionTable<SeparationRequestStatus, Holder>(SeparationRequestStatus.class, Holder::new, h -> h.status);
        table.operation("to REQUESTED", h -> h.to(REQUESTED));
        table.operation("to APPROVED", h -> h.to(APPROVED), REQUESTED, APPROVED, SEPARATING, APPROVED);
        table.operation("to SEPARATING", h -> h.to(SEPARATING), APPROVED, SEPARATING);
        table.operation("to COMPLETED", h -> h.to(COMPLETED), SEPARATING, COMPLETED);
        table.operation("to WITHDRAWN", h -> h.to(WITHDRAWN), REQUESTED, WITHDRAWN, APPROVED, WITHDRAWN);
        table.operation("to REJECTED", h -> h.to(REJECTED), REQUESTED, REJECTED);

        table.verify();
    }
}
