package com.forcitis.one.platform.approval.infrastructure.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** taskUrlTemplate: the page a WeCom card opens; {taskId} is replaced by the task id (no buttons in 1a, architecture §3.7). */
@ConfigurationProperties("forcitis.platform.approval.notification")
record NotificationProperties(String taskUrlTemplate) {

    NotificationProperties {
        taskUrlTemplate = taskUrlTemplate == null || taskUrlTemplate.isBlank()
                ? "https://one.forcitis.com/approval/tasks/{taskId}" : taskUrlTemplate;
    }
}
