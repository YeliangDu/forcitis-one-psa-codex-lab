package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** A refusal is answered whether or not its audit entry could be written; a lost entry is logged and counted. */
@Component
class DenialAudit {

    private static final Logger log = LoggerFactory.getLogger(DenialAudit.class);

    private final AuditLog audit;
    private final MeterRegistry meters;

    DenialAudit(AuditLog audit, MeterRegistry meters) {
        this.audit = audit;
        this.meters = meters;
    }

    void record(AuditEntry entry) {
        try {
            audit.recordIndependently(entry);
        } catch (RuntimeException failed) {
            log.error("Audit entry {} for {} could not be written: {}", entry.action(), entry.subjectKey(), failed.toString());
            meters.counter("platform.approval.denial.audit.failed").increment();
        }
    }
}
