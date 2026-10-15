package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSyncRuns {
    private final SyncRunQueries runs;

    ListSyncRuns(SyncRunQueries runs) {
        this.runs = runs;
    }

    /** Newest first; the cursor is the start time (in microseconds) and id of the run the previous page ended with. */
    @Transactional(readOnly = true)
    public CursorPage<SyncRunView> execute(PageRequest page) {
        SyncRunQueries.Position before = null;
        if (page.cursorValue() != null) {
            try {
                String[] parts = page.cursorValue().split("\\|");
                before = new SyncRunQueries.Position(Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS),
                        new SyncRunId(Uuids.parseCanonical(parts[1])));
            } catch (RuntimeException malformed) {
                throw new InvalidCursorException("the cursor is not one this server issued");
            }
        }
        List<SyncRunRecord> found = runs.findBefore(before, page.limit() + 1);
        List<SyncRunView> items = found.stream().limit(page.limit()).map(SyncRunView::of).toList();
        SyncRunRecord last = found.size() > page.limit() ? found.get(page.limit() - 1) : null;
        String next = last == null ? null : PageRequest.encode("sync-runs",
                ChronoUnit.MICROS.between(Instant.EPOCH, last.startedAt()) + "|" + last.syncRunId());
        return CursorPage.of(items, next);
    }
}
