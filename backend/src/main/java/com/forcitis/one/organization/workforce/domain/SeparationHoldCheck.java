package com.forcitis.one.organization.workforce.domain;

/**
 * The seam for the administrator-separation hold (ADR-0068 item 2, card B6). Card B3 enters every separation straight to pending or effective:
 * the answer here is always no. B6 replaces {@link NeverHold} with the check done under the advisory lock (ADR-0068 item 2.2); the use case
 * asks this once, for a separation, before the status is decided.
 */
public interface SeparationHoldCheck {

    boolean holds(PersonnelCommand separation);
}
