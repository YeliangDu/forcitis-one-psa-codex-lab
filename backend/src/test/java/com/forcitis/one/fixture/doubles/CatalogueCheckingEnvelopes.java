package com.forcitis.one.fixture.doubles;

import com.forcitis.one.kernel.event.EventEnvelopes;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** In every test context the real envelope factory checks each event's businessDate against the catalogue before sealing it. */
@AutoConfiguration
public class CatalogueCheckingEnvelopes {

    @Bean
    static BeanPostProcessor catalogueCheckingEnvelopes() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof EventEnvelopes envelopes && !(bean instanceof TestEventEnvelopes)) {
                    return (EventEnvelopes) draft -> envelopes.wrap(CatalogueBusinessDates.check(draft));
                }
                return bean;
            }
        };
    }
}
