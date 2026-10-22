package com.forcitis.one.platform.approval.api;

import java.util.Locale;

/**
 * One BPMN file per constant (processes/{key}.bpmn20.xml); constant = {SUBDOMAIN}_{BC}_{NAME} of the owning module (G47).
 * The document type a process approves is data of its definition (forcitis:documentType), not a field of the constant (ADR-0066 decision 4).
 */
public enum ProcessKey {
    COST_TIMESHEET_APPROVAL(true);

    private final boolean routingFlagRequired;

    ProcessKey(boolean routingFlagRequired) {
        this.routingFlagRequired = routingFlagRequired;
    }

    /** True when the branches are chosen by routing flags: a start with none would route nowhere and is refused. */
    public boolean routingFlagRequired() {
        return routingFlagRequired;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
