package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;

/** Never taken from request bodies or headers. */
public interface CurrentUser {

    UserId userId();

    LegalEntityId legalEntityId();
}
