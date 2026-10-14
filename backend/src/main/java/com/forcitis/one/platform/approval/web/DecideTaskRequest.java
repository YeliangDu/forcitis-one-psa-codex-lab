package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.DecisionOutcome;

/** Carries no identity: the actor is the session user (G33). */
record DecideTaskRequest(DecisionOutcome outcome, String reason) {
}
