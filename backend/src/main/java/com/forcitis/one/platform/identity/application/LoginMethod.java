package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.integration.api.WecomEntry;

public enum LoginMethod {
    WECOM_WORKBENCH,
    WECOM_MINI_PROGRAM,
    WECOM_QR_CODE_LOGIN,
    SSO,
    SIGNED_TICKET,
    EMERGENCY;

    /** A ticket arrives from another site, so whose identity it is has to be confirmed by the person in front of the screen (C-M7). */
    public boolean requiresIdentityConfirmation() {
        return this == SSO || this == SIGNED_TICKET;
    }

    static LoginMethod of(WecomEntry entry) {
        return switch (entry) {
            case WORKBENCH -> WECOM_WORKBENCH;
            case MINI_PROGRAM -> WECOM_MINI_PROGRAM;
            case QR_CODE_LOGIN -> WECOM_QR_CODE_LOGIN;
        };
    }
}
