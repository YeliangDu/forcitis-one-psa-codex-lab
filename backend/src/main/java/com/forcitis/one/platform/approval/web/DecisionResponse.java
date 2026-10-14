package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.DecisionResult;

record DecisionResponse(String instanceId, String status) {

    static DecisionResponse of(DecisionResult result) {
        return new DecisionResponse(result.instanceId().value(), result.status().name());
    }
}
