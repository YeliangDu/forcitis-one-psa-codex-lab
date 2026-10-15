package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.IdentityError;

final class AccountPath {

    private AccountPath() {
    }

    static UserId user(long raw) {
        if (raw == 0) {
            throw IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND.refusal("no account " + raw);
        }
        return new UserId(raw);
    }
}
