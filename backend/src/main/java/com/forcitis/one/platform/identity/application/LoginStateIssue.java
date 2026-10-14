package com.forcitis.one.platform.identity.application;

import java.time.Duration;

/** state goes to the page; binding goes to the browser as an HttpOnly cookie for as long as the state lives. Neither is ever logged. */
public record LoginStateIssue(String state, String binding, Duration lifetime) {

    @Override
    public String toString() {
        return "LoginStateIssue[]";
    }
}
