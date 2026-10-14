package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSyncRuns {
    private final SyncRunQueries runs;

    ListSyncRuns(SyncRunQueries runs) {
        this.runs = runs;
    }

    /** Newest first; the cursor is the id of the run the previous page ended with. */
    @Transactional(readOnly = true)
    public CursorPage<SyncRunView> execute(PageRequest page) {
        SyncRunId before = null;
        if (page.cursorValue() != null) {
            try {
                before = new SyncRunId(UUID.fromString(page.cursorValue()));
            } catch (IllegalArgumentException malformed) {
                throw new InvalidCursorException("the cursor is not one this server issued");
            }
        }
        List<SyncRunRecord> found = runs.findBefore(before, page.limit() + 1);
        List<SyncRunView> items = found.stream().limit(page.limit()).map(SyncRunView::of).toList();
        String next = found.size() > page.limit() ? PageRequest.encode("sync-runs", String.valueOf(found.get(page.limit() - 1).syncRunId().value())) : null;
        return CursorPage.of(items, next);
    }
}
