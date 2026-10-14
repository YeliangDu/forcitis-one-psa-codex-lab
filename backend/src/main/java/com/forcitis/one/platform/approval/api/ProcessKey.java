package com.forcitis.one.platform.approval.api;

import java.util.Locale;

/**
 * Typed catalogue of deployed approval processes; one BPMN file per constant (processes/{key}.bpmn20.xml).
 * Constant = {SUBDOMAIN}_{BC}_{NAME} of the module that owns the business document (G47).
 */
public enum ProcessKey {
    COST_TIMESHEET_APPROVAL;

    /** The BPMN process id: the constant in lower snake_case, e.g. cost_timesheet_approval. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
