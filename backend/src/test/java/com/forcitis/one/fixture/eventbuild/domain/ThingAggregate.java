package com.forcitis.one.fixture.eventbuild.domain;

import com.forcitis.one.fixture.eventbuild.events.ThingHappened;

/** The aggregate records its own event: the allowed place. */
public class ThingAggregate {
    public ThingHappened happened() {
        return new ThingHappened("t");
    }
}
