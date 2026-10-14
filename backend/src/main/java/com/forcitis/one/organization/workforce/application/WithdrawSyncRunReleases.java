package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A release that could not be completed does not stay half done: the kinds already lifted are taken back, so the run can be released again. */
@Service
class WithdrawSyncRunReleases {
    private final SyncRunRepository runs;

    WithdrawSyncRunReleases(SyncRunRepository runs) {
        this.runs = runs;
    }

    @Transactional
    public void execute(SyncRunId held) {
        runs.find(held).filter(run -> run.status() == SyncRunStatus.HELD).ifPresent(run -> {
            run.withdrawReleases();
            runs.save(run);
        });
    }
}
