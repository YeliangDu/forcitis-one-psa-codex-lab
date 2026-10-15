package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.util.List;

public record UserAccountView(UserId userId, String status, List<String> roleCodes, long version) {
}
