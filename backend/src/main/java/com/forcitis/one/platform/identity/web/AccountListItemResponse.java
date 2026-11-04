package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.AccountListItem;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record AccountListItemResponse(UUID userId, String status, List<String> roleCodes, @Nullable String displayName, @Nullable String employeeCode) {

    static AccountListItemResponse of(AccountListItem item) {
        return new AccountListItemResponse(item.userId().value(), item.status(), item.roleCodes(), item.displayName(), item.employeeCode());
    }
}
