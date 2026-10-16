package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;

/** The audit kind of what an attribution is about; the switch has no default, so a new AttributionKind fails to compile here instead of at a refusal. */
final class AttributionAuditKinds {

    private AttributionAuditKinds() {
    }

    static AuditSubjectKind of(AttributionKind kind) {
        return switch (kind) {
            case ORG_UNIT -> AuditSubjectKind.ORG_UNIT;
            case EMPLOYEE -> AuditSubjectKind.EMPLOYEE;
        };
    }
}
