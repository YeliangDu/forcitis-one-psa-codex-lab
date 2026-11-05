package com.forcitis.one.fixture.twoaggregates.domain;

public interface BetaRepository {
    void save(String value);

    String load();
}
