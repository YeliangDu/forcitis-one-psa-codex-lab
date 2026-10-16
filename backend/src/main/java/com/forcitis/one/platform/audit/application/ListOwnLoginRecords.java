package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.OwnLoginRecordPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The person is the caller, never a parameter: nobody asks for someone else's records through this use case (ADR-0027 section 3). */
@Service
public class ListOwnLoginRecords {
    private static final String LIST = "own-login-records";

    private final AuditEntryQueries entries;

    ListOwnLoginRecords(AuditEntryQueries entries) {
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public OwnLoginRecordPageView execute(UserId caller, PageRequest paging) {
        OwnLoginRecordPage page = entries.findOwnLoginRecords(caller, AuditCursor.decode(paging), paging.limit());
        return new OwnLoginRecordPageView(page.items().stream().map(OwnLoginRecordView::of).toList(),
                page.next() == null ? null : AuditCursor.encode(LIST, page.next()));
    }
}
