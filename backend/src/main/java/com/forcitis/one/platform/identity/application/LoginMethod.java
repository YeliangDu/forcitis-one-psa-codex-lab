package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.integration.api.WecomEntry;

/** How a session was started; recorded in the audit trail and on the session. */
public enum LoginMethod {
    WECOM_WORKBENCH,
    WECOM_MINI_PROGRAM,
    WECOM_WEB_SCAN,
    SEEYON,
    SIGNED_TICKET,
    EMERGENCY;

    static LoginMethod of(WecomEntry entry) {
        return switch (entry) {
            case WORKBENCH -> WECOM_WORKBENCH;
            case MINI_PROGRAM -> WECOM_MINI_PROGRAM;
            case WEB_SCAN -> WECOM_WEB_SCAN;
        };
    }
}
