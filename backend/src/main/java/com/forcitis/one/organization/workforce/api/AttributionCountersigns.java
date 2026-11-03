package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import org.jspecify.annotations.Nullable;

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

    /**
     * The requests the caller may decide (default status OPEN), earliest due first. A request is visible when its legal entity has the caller as finance head in
     * today's version, or has escalated to level 2 and the caller is a business administrator. Needs the same permission as approve; a caller who sees nothing gets an
     * empty page. Reading is not audited.
     */
    CursorPage<AttributionCountersignSummary> list(@Nullable AttributionCountersignStatus status, PageRequest page);

    /** One visible request; an id that does not exist and one that is not visible give the same refusal, ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND. */
    AttributionCountersignDetail get(AttributionCountersignId id);
}
