package com.forcitis.one.organization.workforce.api;

/**
 * What the organisation says about one employee on one business day when administrators are counted (ADR-0068 sections 2.2 and 3; user decision 2026-10-30
 * 「统一都算已走」). onUnpaidLeave: an employed version flagged unpaid leave that day. separationHeld: a separation is held. leaving: the one "already gone" count:
 * separation held, separation released and waiting for its date, or an unpaid-leave start entered and not yet started; a person who is leaving is still employed.
 */
public record Availability(boolean onUnpaidLeave, boolean separationHeld, boolean leaving) {

    public static final Availability AVAILABLE = new Availability(false, false, false);
}
