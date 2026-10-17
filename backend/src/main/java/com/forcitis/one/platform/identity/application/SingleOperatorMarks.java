package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.identity.api.SingleOperatorMode;
import com.forcitis.one.platform.identity.api.SingleOperatorRelaxation;
import org.springframework.stereotype.Component;

/** The mark in the audit trail that a relaxation of the single operator mode was used (ADR-0030 section 2). It joins the caller's transaction. */
@Component
class SingleOperatorMarks {

    private final AuditLog audit;
    private final SingleOperatorMode mode;

    SingleOperatorMarks(AuditLog audit, SingleOperatorMode mode) {
        this.audit = audit;
        this.mode = mode;
    }

    void witnessOmitted(OperationAttestation attestation, AuditSubject subject) {
        if (attestation.witnessOmitted()) {
            mark(SingleOperatorRelaxation.WITNESS_OMITTED, subject);
        }
    }

    void mark(SingleOperatorRelaxation relaxation, AuditSubject subject) {
        if (!mode.enabled()) {
            throw new IllegalStateException("a relaxation is marked only while the single operator mode is on");
        }
        audit.record(new AuditEntry("platform.identity.single-operator-mode.applied", null, null, subject, "relaxation=" + relaxation.name()));
    }
}
