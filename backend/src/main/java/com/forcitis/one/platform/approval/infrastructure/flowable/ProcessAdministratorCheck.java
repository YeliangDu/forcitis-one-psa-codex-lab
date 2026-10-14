package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A task nobody can be found for goes to the holders of the process administrator role (D-1a-61); with no holder it would have nowhere
 * to go. The check is on in every environment; only a test configuration switches it off, explicitly.
 */
@Component
class ProcessAdministratorCheck {

    private static final Logger log = LoggerFactory.getLogger(ProcessAdministratorCheck.class);

    private final ProcessAdministrator administrator;
    private final AssignmentProperties properties;
    private final boolean required;

    ProcessAdministratorCheck(ProcessAdministrator administrator, AssignmentProperties properties,
            @Value("${forcitis.platform.approval.process-administrator-required:true}") boolean required) {
        this.administrator = administrator;
        this.properties = properties;
        this.required = required;
    }

    boolean isRequired() {
        return required;
    }

    void verify() {
        if (administrator.holders().isEmpty()) {
            String message = "Nobody holds the role '" + properties.processAdministratorRole() + "' (or it does not exist): a task nobody can be found for"
                    + " would have nowhere to go (D-1a-61)";
            if (required) {
                throw new IllegalStateException(message);
            }
            log.warn("{}", message);
        }
    }
}
