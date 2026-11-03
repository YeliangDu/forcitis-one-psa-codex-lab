package com.forcitis.one.organization.workforce.api;

/**
 * The finance countersign of a change of the cost legal entity, and of its withdrawal (ADR-0073 item 3.6). Both calls need
 * organization.workforce:legal-entity-attribution:approve, and the caller must be the finance head of the request's legal entity in today's version, or
 * a business administrator when the request has escalated to level 2. The decider is always the signed-in caller.
 */
public interface AttributionCountersigns {

    /** The last approval of a command's required set writes its versions in this transaction; a command dated today or earlier takes effect. */
    void approve(ApproveAttributionCountersign request);

    /** A rejection withdraws a command that awaits countersign; for a withdrawal request it leaves the command standing. */
    void reject(RejectAttributionCountersign request);
}
