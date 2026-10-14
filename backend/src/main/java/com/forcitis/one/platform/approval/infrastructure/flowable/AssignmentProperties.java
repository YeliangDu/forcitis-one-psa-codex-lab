package com.forcitis.one.platform.approval.infrastructure.flowable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** processAdministratorRole is the code of the role whose holders receive tasks nobody could be found for (D-1a-61); roles are data of platform.identity. */
@ConfigurationProperties("forcitis.platform.approval.assignment")
record AssignmentProperties(String processAdministratorRole) {

    AssignmentProperties {
        if (processAdministratorRole == null || processAdministratorRole.isBlank()) {
            throw new IllegalArgumentException("forcitis.platform.approval.assignment.process-administrator-role must name a role");
        }
    }
}
