package com.forcitis.one.fixture.aggregates.domain;

/** A named repository for SingleAggregateGuardTest: the guard's pointcut names `..domain.*Repository`, so the proxied target is a real class there, not a lambda. */
public final class NoopSampleRepository implements SampleRepository {

    @Override
    public void save(Object aggregate) {
    }
}
