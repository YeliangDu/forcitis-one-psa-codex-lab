package com.forcitis.one.fixture.readmodel.application;

import com.forcitis.one.fixture.readmodel.domain.ThingRepository;

public class ListThings {
    private final ThingRepository things;

    public ListThings(ThingRepository things) {
        this.things = things;
    }

    public String execute() {
        return things.load();
    }
}
