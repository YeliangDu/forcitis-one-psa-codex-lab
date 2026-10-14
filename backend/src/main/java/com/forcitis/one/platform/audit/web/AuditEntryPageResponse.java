package com.forcitis.one.platform.audit.web;

import com.forcitis.one.platform.audit.application.AuditEntryPageView;
import java.util.List;

record AuditEntryPageResponse(List<AuditEntryResponse> items, int page, int limit, long matchCount) {

    static AuditEntryPageResponse of(AuditEntryPageView view) {
        return new AuditEntryPageResponse(view.items().stream().map(AuditEntryResponse::of).toList(), view.page(), view.limit(),
                view.matchCount());
    }
}
