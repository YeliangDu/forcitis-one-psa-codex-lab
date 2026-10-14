package com.forcitis.one.platform.audit.application;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;

final class Clocks {

    private Clocks() {
    }

    static Clock from(ObjectProvider<Clock> provider) {
        return provider.getIfAvailable(Clock::systemUTC);
    }
}
