package com.forcitis.one.fixture.readmodel.application;

import com.forcitis.one.fixture.readmodel.domain.ThingQueries;

public class GetThing {
    private final ThingQueries things;

    public GetThing(ThingQueries things) {
        this.things = things;
    }

    public String execute() {
        return things.read();
    }
}
