package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repeating it changes nothing; the trigger for an employee's departure is wired to this one method. */
@Service
public class RevokeBrowserIdentifiers {

    private final BrowserIdentity browserIdentity;

    RevokeBrowserIdentifiers(BrowserIdentity browserIdentity) {
        this.browserIdentity = browserIdentity;
    }

    @Transactional
    public void execute(UserId employeeId) {
        browserIdentity.invalidateAll(employeeId);
    }
}
