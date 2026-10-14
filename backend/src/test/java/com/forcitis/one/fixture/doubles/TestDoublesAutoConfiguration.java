package com.forcitis.one.fixture.doubles;

import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Stand-ins for contracts whose real implementation lives in another module; they back off as soon as the real bean exists. */
@AutoConfiguration
public class TestDoublesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventEnvelopes.class)
    TestEventEnvelopes eventEnvelopes() {
        return new TestEventEnvelopes();
    }

    @Bean
    @ConditionalOnMissingBean(WorkCalendar.class)
    TestWorkCalendar workCalendar() {
        return new TestWorkCalendar();
    }
}
