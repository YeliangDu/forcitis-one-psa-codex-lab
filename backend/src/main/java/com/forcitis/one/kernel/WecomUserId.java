package com.forcitis.one.kernel;

import java.util.Objects;

/** WeCom (企业微信) user id as stored in A8 ORG_MEMBER.we_com_id; the only key for WeCom login and messages (RULE-322/374). */
@ValueObject
public record WecomUserId(String value) {

    public WecomUserId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("WecomUserId must not be blank");
        }
    }
}
