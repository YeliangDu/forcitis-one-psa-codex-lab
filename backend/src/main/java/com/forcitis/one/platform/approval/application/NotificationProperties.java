package com.forcitis.one.platform.approval.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {taskId} in taskUrlTemplate is replaced by the task id. */
@ConfigurationProperties("forcitis.platform.approval.notification")
record NotificationProperties(String taskUrlTemplate) {

    NotificationProperties {
        taskUrlTemplate = taskUrlTemplate == null || taskUrlTemplate.isBlank()
                ? "https://one.forcitis.com/approval/tasks/{taskId}" : taskUrlTemplate;
    }
}
