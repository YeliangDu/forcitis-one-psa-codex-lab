package com.forcitis.one.organization.workforce.infrastructure.scheduling;

import com.forcitis.one.organization.workforce.application.RefreshShortNames;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "forcitis.organization.workforce.short-name", name = "enabled", havingValue = "true")
class ShortNameRefreshJob {
    private final RefreshShortNames refresh;

    ShortNameRefreshJob(RefreshShortNames refresh) {
        this.refresh = refresh;
    }

    @Scheduled(cron = "${forcitis.organization.workforce.short-name.cron}")
    @SchedulerLock(name = "organization.workforce.short.name.refresh", lockAtMostFor = "PT10M")
    void refreshShortNames() {
        refresh.execute();
    }
}
