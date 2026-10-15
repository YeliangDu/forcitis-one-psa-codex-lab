package com.forcitis.one.platform.approval.web;

import java.util.UUID;

/** Carries no caller identity: the process administrator is the session user (G33). The reason is required (D-1a-61). */
record ReassignTaskRequest(UUID assigneeId, String reason) {
}
