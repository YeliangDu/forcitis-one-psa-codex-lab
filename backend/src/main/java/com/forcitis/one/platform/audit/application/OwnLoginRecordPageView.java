package com.forcitis.one.platform.audit.application;

import java.util.List;

/** nextCursor is null on the last page. */
public record OwnLoginRecordPageView(List<OwnLoginRecordView> items, String nextCursor) {
}
