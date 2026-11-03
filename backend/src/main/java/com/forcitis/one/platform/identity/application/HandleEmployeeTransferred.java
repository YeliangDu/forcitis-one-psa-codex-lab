package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.organization.workforce.events.EmployeeTransferred;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A transfer with no department before it cannot be a transfer (ADR-0081 decision 3, ADR-0082 decision 2): it is refused with one alert line and
 * consumed, because a redelivery would be refused the same way. Raising the review items of a real transfer follows with its own acceptance.
 */
@SystemActorName("platform.identity.permission-adjustment")
@Component
class HandleEmployeeTransferred {

    private static final Logger LOG = LoggerFactory.getLogger(HandleEmployeeTransferred.class);

    @ApplicationModuleListener
    void on(EmployeeTransferred event) {
        if (event.previousOrgUnitId() == null) {
            LOG.error("ALERT P2 platform.identity event {} ({}) has no department before the transfer: refused, no review item raised",
                    event.envelope().eventId(), event.envelope().eventType());
        }
    }
}
