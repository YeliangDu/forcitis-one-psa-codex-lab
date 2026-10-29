package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Data scorecard K7 (GUIDING_PRINCIPLES section 6): a delivery that was sent or deliberately skipped is kept 90 days, one that
 * failed for good (failed, dead letter) one year. A delivery still pending is never removed, and a card stays while a
 * handled note that replaces it has not yet expired (the note's foreign key to the card).
 */
@Component
@EnableConfigurationProperties(OutboundDeliveryPurgeJob.KeepPeriods.class)
@ConditionalOnProperty(prefix = "forcitis.platform.integration.outbound-purge", name = "enabled", havingValue = "true", matchIfMissing = true)
class OutboundDeliveryPurgeJob {
    private static final Logger LOG = LoggerFactory.getLogger(OutboundDeliveryPurgeJob.class);

    @ConfigurationProperties("forcitis.platform.integration.outbound-purge")
    record KeepPeriods(Period deliveredKeepPeriod, Period failedKeepPeriod) {
        KeepPeriods {
            require(deliveredKeepPeriod, "delivered-keep-period");
            require(failedKeepPeriod, "failed-keep-period");
        }

        private static void require(Period period, String key) {
            if (period == null || period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("forcitis.platform.integration.outbound-purge." + key + " is required and must be positive");
            }
        }
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final BusinessZone zone;
    private final KeepPeriods keep;

    OutboundDeliveryPurgeJob(JdbcClient jdbc, Clock clock, BusinessZone zone, KeepPeriods keep) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
        this.keep = keep;
    }

    @Scheduled(cron = "${forcitis.platform.integration.outbound-purge.cron}")
    @SchedulerLock(name = "platform.integration.outbound.purge", lockAtMostFor = "PT30M")
    void purge() {
        int removed = delete();
        LOG.info("platform.integration removed {} deliveries: delivered ones older than {}, failed ones older than {}", removed,
                keep.deliveredKeepPeriod(), keep.failedKeepPeriod());
    }

    private int delete() {
        return jdbc.sql("""
                delete from platform_integration.outbound_deliveries c
                where (c.status in ('SENT', 'SKIPPED') and c.updated_at < :deliveredCutoff
                       or c.status in ('FAILED', 'DEAD_LETTER') and c.updated_at < :failedCutoff)
                  and not exists (
                      select 1 from platform_integration.outbound_deliveries n
                      where n.target_delivery_id = c.id
                        and not (n.status in ('SENT', 'SKIPPED') and n.updated_at < :deliveredCutoff
                                 or n.status in ('FAILED', 'DEAD_LETTER') and n.updated_at < :failedCutoff))
                """)
                .param("deliveredCutoff", cutoff(keep.deliveredKeepPeriod()))
                .param("failedCutoff", cutoff(keep.failedKeepPeriod())).update();
    }

    private OffsetDateTime cutoff(Period period) {
        return OffsetDateTime.ofInstant(Instant.from(clock.instant().atZone(zone.zone()).minus(period)), ZoneOffset.UTC);
    }
}
