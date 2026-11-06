package com.forcitis.one.platform.audit.infrastructure.archive;

import com.forcitis.one.kernel.LocalDueJobs;
import com.forcitis.one.kernel.SettableClock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Card #1478, profile local only: an audit entry of a month without a partition is refused, and the partition job creates only the months from the real
 * date on. When the settable clock is moved to a pretend date, this makes the partitions of that month and of the months after it (through the most the
 * database function creates, 24 months) exist before anything is written. First in line: the other daily jobs write audit entries.
 */
@Component
@Profile("local")
@Order(Ordered.HIGHEST_PRECEDENCE)
class LocalAuditPartitions implements LocalDueJobs {
    private static final int MONTHS_AHEAD = 23;

    private final JdbcClient jdbc;
    private final SettableClock clock;

    LocalAuditPartitions(JdbcClient jdbc, SettableClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void runOnce() {
        LocalDate thisMonth = clock.instant().atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        jdbc.sql("select platform_audit.create_audit_entry_partitions(:from, :through)")
                .param("from", thisMonth).param("through", thisMonth.plusMonths(MONTHS_AHEAD)).query(Integer.class).single();
    }
}
