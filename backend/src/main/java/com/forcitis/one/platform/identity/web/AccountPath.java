package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.UserId;
import java.util.UUID;

final class AccountPath {

    private AccountPath() {
    }

    static UserId user(UUID raw) {
        return new UserId(raw);
    }
}
