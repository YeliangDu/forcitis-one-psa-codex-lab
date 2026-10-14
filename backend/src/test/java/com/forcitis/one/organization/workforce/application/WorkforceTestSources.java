package com.forcitis.one.organization.workforce.application;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the (disabled) the source and old-system sources of the application context with in-memory stand-ins. */
@TestConfiguration
public class WorkforceTestSources {

    @Bean
    @Primary
    public FakeOrgSource fakeOrgSource() {
        return new FakeOrgSource();
    }

    @Bean
    @Primary
    public FakeShortNameSource fakeShortNameSource() {
        return new FakeShortNameSource();
    }
}
