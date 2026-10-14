package com.forcitis.one.platform.approval.infrastructure.flowable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** processAdministratorUserId receives tasks nobody could be found for; must be set in every real environment. */
@ConfigurationProperties("forcitis.platform.approval.assignment")
record AssignmentProperties(Long processAdministratorUserId) {
}
