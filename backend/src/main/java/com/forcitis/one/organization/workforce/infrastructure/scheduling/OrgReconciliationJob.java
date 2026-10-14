package com.forcitis.one.organization.workforce.infrastructure.scheduling;

import com.forcitis.one.organization.workforce.application.ReconcileOrgSnapshot;
import com.forcitis.one.organization.workforce.domain.OrgReconciliationReport;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Once a day the snapshot is compared with the source; a difference is an alert and stays visible in the gauge until it is zero. */
@Component
@ConditionalOnProperty(prefix = "forcitis.organization.workforce.reconciliation", name = "enabled", havingValue = "true")
class OrgReconciliationJob {
    private static final Logger LOG = LoggerFactory.getLogger(OrgReconciliationJob.class);

    private final ReconcileOrgSnapshot reconcile;
    private final AtomicInteger differences = new AtomicInteger();

    OrgReconciliationJob(ReconcileOrgSnapshot reconcile, MeterRegistry registry) {
        this.reconcile = reconcile;
        Gauge.builder("forcitis.organization.workforce.reconciliation.differences", differences, AtomicInteger::get)
                .description("Differences between the org snapshot and the source at the latest daily comparison; the exit criterion is zero")
                .register(registry);
    }

    @Scheduled(cron = "${forcitis.organization.workforce.reconciliation.cron}")
    @SchedulerLock(name = "organization.workforce.snapshot.reconcile", lockAtMostFor = "PT10M")
    void compare() {
        OrgReconciliationReport report = reconcile.execute();
        differences.set(report.differenceCount());
        if (!report.isZeroDifference()) {
            LOG.error("ALERT organization.workforce snapshot differs from the source in {} places: missing {}, unexpected {}, parents {}, "
                            + "reporting lines {}, heads and managers {}, WeCom ids {}", report.differenceCount(), report.missingInSnapshot().size(),
                    report.unexpectedInSnapshot().size(), report.parentMismatches().size(), report.reportingLineMismatches().size(),
                    report.responsibilityMismatches().size(), report.wecomUserIdMismatches().size());
        }
    }
}
