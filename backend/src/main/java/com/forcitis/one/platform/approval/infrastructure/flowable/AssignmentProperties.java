package com.forcitis.one.platform.approval.infrastructure.flowable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Assignment settings. processAdministratorUserId is the process administrator who receives a task nobody could be found for
 * (RULE-250/254); it replaces the old hard-coded personal fallback and must be set in every real environment.
 */
@ConfigurationProperties("forcitis.platform.approval.assignment")
record AssignmentProperties(Long processAdministratorUserId) {
}
