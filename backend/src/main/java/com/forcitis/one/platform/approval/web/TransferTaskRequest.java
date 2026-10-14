package com.forcitis.one.platform.approval.web;

/** Carries no caller identity: the person handing the task over is the session user (G33). */
record TransferTaskRequest(long assigneeId) {
}
