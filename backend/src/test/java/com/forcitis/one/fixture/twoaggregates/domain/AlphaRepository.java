package com.forcitis.one.fixture.twoaggregates.domain;

public interface AlphaRepository {
    void save(String value);

    String load();
}
