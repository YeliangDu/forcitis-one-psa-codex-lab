package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.DocumentType;
import java.util.Locale;

/** One BPMN file per constant (processes/{key}.bpmn20.xml); constant = {SUBDOMAIN}_{BC}_{NAME} of the owning module (G47). */
public enum ProcessKey {
    COST_TIMESHEET_APPROVAL(true, DocumentType.TIMESHEET);

    private final boolean routingFlagRequired;
    private final DocumentType documentType;

    ProcessKey(boolean routingFlagRequired, DocumentType documentType) {
        this.routingFlagRequired = routingFlagRequired;
        this.documentType = documentType;
    }

    /** True when the branches are chosen by routing flags: a start with none would route nowhere and is refused. */
    public boolean routingFlagRequired() {
        return routingFlagRequired;
    }

    public DocumentType documentType() {
        return documentType;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
