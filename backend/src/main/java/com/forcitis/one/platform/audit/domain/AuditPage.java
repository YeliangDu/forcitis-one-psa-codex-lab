package com.forcitis.one.platform.audit.domain;

import java.util.List;

public record AuditPage(List<AuditRecord> items, int page, int limit, long matchCount) {

    public AuditPage {
        items = List.copyOf(items);
    }
}
