package com.forcitis.one.platform.approval.web;

/** Return a task to the initiator; the reason is required (RULE-275). Carries no identity (G33). */
record ReturnTaskRequest(String reason) {
}
