package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;

/** Who is calling, resolved from the server-side session. Never taken from request bodies or headers. */
public interface CurrentUser {

    UserId userId();

    LegalEntityId legalEntityId();
}
