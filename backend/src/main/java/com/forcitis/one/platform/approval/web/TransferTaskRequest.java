package com.forcitis.one.platform.approval.web;

/** Carries no caller identity: the person handing the task over is the session user (G33). The reason is required (D-1a-75). */
record TransferTaskRequest(long assigneeId, String reason) {
}
