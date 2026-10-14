package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.UserAccountView;
import java.util.List;

/** userId is a string: external ids are signed 64-bit numbers, which JavaScript numbers cannot hold. */
record UserAccountResponse(String userId, String status, List<String> roleCodes) {

    static UserAccountResponse of(UserAccountView view) {
        return new UserAccountResponse(String.valueOf(view.userId()), view.status(), view.roleCodes());
    }
}
