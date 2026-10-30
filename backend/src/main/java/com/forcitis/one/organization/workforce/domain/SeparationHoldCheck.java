package com.forcitis.one.organization.workforce.domain;

/**
 * Whether a separation is held instead of taking effect (ADR-0068 item 2.2): decided once, at entry, under the separation lock the use case has
 * already taken, before the status is decided.
 */
public interface SeparationHoldCheck {

    boolean holds(PersonnelCommand separation);
}
