package com.forcitis.one.accounting.ledger.infrastructure.startup;

import com.forcitis.one.accounting.ledger.application.BackfillLedgers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Legal entities that existed before the ledger module get their books once the application is up; later runs find nothing to create. */
@Component
class LedgerBackfillOnStart implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(LedgerBackfillOnStart.class);

    private final BackfillLedgers backfill;

    LedgerBackfillOnStart(BackfillLedgers backfill) {
        this.backfill = backfill;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = backfill.execute();
        if (created > 0) {
            LOG.info("created {} missing ledgers", created);
        }
    }
}
