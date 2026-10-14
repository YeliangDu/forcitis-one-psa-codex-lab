package com.forcitis.one.platform.audit.domain;

import java.util.List;

/** The actions a person sees about themselves: logins, logouts and ended sessions (including forced logout and separation). */
public final class OwnLoginRecords {

    public static final List<String> ACTIONS = List.of(
            "platform.identity.login.succeeded",
            "platform.identity.login.failed",
            "platform.identity.logout",
            "platform.identity.session.revoked");

    private OwnLoginRecords() {
    }
}
