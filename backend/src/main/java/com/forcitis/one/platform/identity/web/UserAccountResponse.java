package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.UserAccountView;
import java.util.List;
import java.util.UUID;

record UserAccountResponse(UUID userId, String status, List<String> roleCodes) {

    static UserAccountResponse of(UserAccountView view) {
        return new UserAccountResponse(view.userId().value(), view.status(), view.roleCodes());
    }
}
