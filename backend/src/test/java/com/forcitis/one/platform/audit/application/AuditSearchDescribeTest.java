package com.forcitis.one.platform.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.audit.api.AuditOutcome;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditSearchDescribeTest {
    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: the filter text of a search or export audit never carries the source address, the other conditions stay")
    void describeLeavesTheSourceAddressOut() {
        UUID actor = UUID.fromString("0192f8a0-0000-7000-8000-000000000001");
        AuditSearch search = new AuditSearch(actor, null, "platform.identity.login.failed", null, null, null, "203.0.113.77", AuditOutcome.FAILED, null, null);

        assertThat(search.describe()).isEqualTo("actorId=" + actor + ",action=platform.identity.login.failed,outcome=FAILED")
                .doesNotContain("sourceAddress", "203.0.113.77");
    }
}
