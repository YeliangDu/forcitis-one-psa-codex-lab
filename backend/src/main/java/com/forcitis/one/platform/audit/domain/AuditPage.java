package com.forcitis.one.platform.audit.domain;

import java.util.List;

/** next is the position to continue after, null on the last page. */
public record AuditPage(List<AuditRecord> items, AuditPosition next) {

    public AuditPage {
        items = List.copyOf(items);
    }
}
