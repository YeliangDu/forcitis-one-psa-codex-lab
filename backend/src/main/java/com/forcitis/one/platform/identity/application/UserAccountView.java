package com.forcitis.one.platform.identity.application;

import java.util.List;

public record UserAccountView(long userId, String status, List<String> roleCodes, long version) {
}
