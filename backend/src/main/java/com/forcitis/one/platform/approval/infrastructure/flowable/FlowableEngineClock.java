package com.forcitis.one.platform.approval.infrastructure.flowable;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.flowable.common.engine.impl.runtime.Clock;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.stereotype.Component;

/** The process engine reads the one injected clock (UTC) and cannot be moved by anyone else (FLW-1). */
@Component
class FlowableEngineClock implements Clock, EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private final java.time.Clock clock;

    FlowableEngineClock(java.time.Clock clock) {
        this.clock = clock;
    }

    @Override
    public void configure(SpringProcessEngineConfiguration configuration) {
        configuration.setClock(this);
    }

    @Override
    public Date getCurrentTime() {
        return Date.from(clock.instant());
    }

    @Override
    public Calendar getCurrentCalendar() {
        return getCurrentCalendar(UTC);
    }

    @Override
    public Calendar getCurrentCalendar(TimeZone timeZone) {
        Calendar calendar = new GregorianCalendar(timeZone);
        calendar.setTime(getCurrentTime());
        return calendar;
    }

    @Override
    public TimeZone getCurrentTimeZone() {
        return UTC;
    }

    @Override
    public void setCurrentTime(Date time) {
        throw new UnsupportedOperationException("the engine clock is the injected clock and cannot be set");
    }

    @Override
    public void setCurrentCalendar(Calendar calendar) {
        throw new UnsupportedOperationException("the engine clock is the injected clock and cannot be set");
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("the engine clock is the injected clock and cannot be reset");
    }
}
