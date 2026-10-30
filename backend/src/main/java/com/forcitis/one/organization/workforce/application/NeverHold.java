package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import org.springframework.stereotype.Component;

/** B3's answer to {@link SeparationHoldCheck}: nothing is held yet; card B6 replaces this bean. */
@Component
class NeverHold implements SeparationHoldCheck {

    @Override
    public boolean holds(PersonnelCommand separation) {
        return false;
    }
}
