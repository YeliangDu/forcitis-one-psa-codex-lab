package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.integration.api.WecomEntry;

/** code and state come from the WeCom redirect; the browser posts them in the body, never in a URL we log. */
public record WecomLoginCommand(String code, String state, WecomEntry entry) {

    @Override
    public String toString() {
        return "WecomLoginCommand[entry=" + entry + "]";
    }
}
