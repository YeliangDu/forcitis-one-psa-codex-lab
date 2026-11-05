package com.forcitis.one.fixture.eventbuild.application;

import com.forcitis.one.fixture.eventbuild.events.ThingHappened;

/** A use case that makes the event itself: the event no longer comes from the aggregate. */
public class ThingUseCase {
    public ThingHappened execute() {
        return new ThingHappened("t");
    }
}
