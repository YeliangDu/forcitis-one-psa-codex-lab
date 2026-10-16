package com.forcitis.one.platform.audit.domain;

import java.util.List;

/** next is the position to continue after, null on the last page. */
public record OwnLoginRecordPage(List<OwnLoginRecord> items, AuditPosition next) {

    public OwnLoginRecordPage {
        items = List.copyOf(items);
    }
}
