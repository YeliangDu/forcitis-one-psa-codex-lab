package com.forcitis.one.cost.timesheet.domain;

import java.util.UUID;

/** Events arrive at least once; a consumer handles an event id only the first time. */
public interface ConsumedEvents {

    boolean firstTime(UUID eventId, String consumerName);
}
