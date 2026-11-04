package com.forcitis.one.organization.workforce.application;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class WorkforceTestSources {
    @Bean
    @Primary
    public AdjustableClock adjustableClock() {
        return new AdjustableClock();
    }

    @Bean
    @Primary
    public FakeOrgSource fakeOrgSource() {
        return new FakeOrgSource();
    }

    @Bean
    public com.forcitis.one.fixture.doubles.AggregateWriteGuard aggregateWriteGuard() {
        return new com.forcitis.one.fixture.doubles.AggregateWriteGuard();
    }
}
