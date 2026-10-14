package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.integration.api.WecomEntry;

/** The browser posts code and state in the body, never in a URL we log. */
public record WecomLoginCommand(String code, String state, WecomEntry entry) {

    @Override
    public String toString() {
        return "WecomLoginCommand[entry=" + entry + "]";
    }
}
