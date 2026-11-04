package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** displayName and employeeCode are null when the employee row of the account is missing. */
public record AccountListItem(UserId userId, String status, List<String> roleCodes, @Nullable String displayName, @Nullable String employeeCode) {
}
