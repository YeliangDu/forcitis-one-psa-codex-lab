package com.forcitis.one.platform.integration.api;

/** INTERACTIVE: a person is waiting (login, merged inbox). BACKGROUND: a scheduled job or a start-up check. */
public enum ExternalCallKind {
    INTERACTIVE,
    BACKGROUND
}
