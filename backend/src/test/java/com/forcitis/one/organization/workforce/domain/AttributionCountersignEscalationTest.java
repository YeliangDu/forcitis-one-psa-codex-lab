package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;

import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("#896 the escalation level of a countersign request")
class AttributionCountersignEscalationTest {

    private static final Instant AT = Instant.parse("2026-11-03T01:00:00Z");

    private static AttributionCountersign row(int level, String decision) {
        return AttributionCountersign.restore(new AttributionCountersignId(UUID.randomUUID()), null, new OrgStructureCommandId(UUID.randomUUID()), null,
                new UserId(UUID.randomUUID()), CountersignRequestKind.CHANGE, 1, new LegalEntityId(UUID.randomUUID()), AT, new UserId(UUID.randomUUID()),
                LocalDate.of(2026, 11, 5), level, null, 0, decision, decision == null ? null : AT, decision == null ? null : new UserId(UUID.randomUUID()), null);
    }

    @Test
    @DisplayName("the level rises step by step, also over several levels at once")
    void rises() {
        AttributionCountersign request = row(0, null);
        request.escalateTo(1);
        request.escalateTo(3);
        assertThat(request.escalationLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("the level never stays, falls or passes the last level")
    void onlyRises() {
        AttributionCountersign request = row(2, null);
        assertThatThrownBy(() -> request.escalateTo(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request.escalateTo(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request.escalateTo(AttributionCountersign.LAST_ESCALATION_LEVEL + 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(request.escalationLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("a decided or lapsed request never escalates")
    void closedRowsStay() {
        for (String decision : new String[] {"APPROVED", "REJECTED", "LAPSED"}) {
            AttributionCountersign request = row(0, decision);
            assertThatThrownBy(() -> request.escalateTo(1)).isInstanceOf(DomainException.class);
            assertThat(request.escalationLevel()).isZero();
        }
    }
}
