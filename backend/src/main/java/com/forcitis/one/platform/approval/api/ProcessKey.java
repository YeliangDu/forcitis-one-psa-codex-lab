package com.forcitis.one.platform.approval.api;

import java.util.Locale;

/** One BPMN file per constant (processes/{key}.bpmn20.xml); constant = {SUBDOMAIN}_{BC}_{NAME} of the owning module (G47). */
public enum ProcessKey {
    COST_TIMESHEET_APPROVAL;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
