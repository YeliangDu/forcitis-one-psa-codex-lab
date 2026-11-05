package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.CountersignStatus.APPROVED;
import static com.forcitis.one.organization.workforce.domain.CountersignStatus.LAPSED;
import static com.forcitis.one.organization.workforce.domain.CountersignStatus.OPEN;
import static com.forcitis.one.organization.workforce.domain.CountersignStatus.REJECTED;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributionCountersignStateMachineTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    static final UserId FINANCE = new UserId(TestIds.uuid(5));

    static AttributionCountersign at(CountersignStatus status) {
        boolean decided = status != OPEN;
        return AttributionCountersign.restore(new AttributionCountersignId(TestIds.uuid(1)), new PersonnelCommandId(TestIds.uuid(2)), null,
                new EmployeeId(TestIds.uuid(3)), new UserId(TestIds.uuid(4)), CountersignRequestKind.CHANGE, 1, new LegalEntityId(TestIds.uuid(6)), NOW,
                new UserId(TestIds.uuid(4)), LocalDate.parse("2026-10-20"), 0, "please", 2, decided ? status.name() : null,
                decided ? NOW : null, status == LAPSED ? null : decided ? FINANCE : null, status == REJECTED ? "no" : null);
    }

    @Test
    @DisplayName("approve, reject and close are tried from every status: each leaves only OPEN")
    void exhaustiveTransitions() {
        new TransitionTable<CountersignStatus, AttributionCountersign>(CountersignStatus.class, AttributionCountersignStateMachineTest::at, AttributionCountersign::status)
                .operation("approve", c -> c.approve(FINANCE, null, NOW), OPEN, APPROVED)
                .operation("reject", c -> c.reject(FINANCE, "no", NOW), OPEN, REJECTED)
                .operation("close", c -> c.close(NOW), OPEN, LAPSED)
                .verify();
    }
}
