package com.forcitis.one.cost.timesheet.domain;

import java.util.UUID;

/** Events arrive at least once; a consumer handles an event id only the first time. */
public interface ConsumedEvents {

    /** True the first time this consumer sees the event, false for every repeat. */
    boolean firstTime(UUID eventId, String consumerName);
}
