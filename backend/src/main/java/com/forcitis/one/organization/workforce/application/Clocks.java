package com.forcitis.one.organization.workforce.application;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;

/** Time always comes from an injected Clock; without a configured one the system UTC clock is used. */
final class Clocks {

    private Clocks() {
    }

    static Clock from(ObjectProvider<Clock> provider) {
        return provider.getIfAvailable(Clock::systemUTC);
    }
}
