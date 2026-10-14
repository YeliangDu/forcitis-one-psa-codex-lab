package com.forcitis.one.platform.audit.application;

import java.util.List;

public record AuditEntryPageView(List<AuditEntryView> items, int page, int limit, long matchCount) {
}
