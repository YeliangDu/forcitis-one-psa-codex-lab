package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.ValueObject;

/** Why a review item is upheld: not blank, at most 500 characters (not bytes; ADR-0044 decision 2 item 2). */
@ValueObject
public record UpholdReason(String value) {

    public static final int MAX_CHARACTERS = 500;

    public UpholdReason {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("the reason of an uphold must not be blank");
        }
        if (value.indexOf('\u0000') >= 0) {
            throw new InvalidRequestException("the reason of an uphold must not contain a NUL character");
        }
        if (value.codePointCount(0, value.length()) > MAX_CHARACTERS) {
            throw new InvalidRequestException("the reason of an uphold is at most " + MAX_CHARACTERS + " characters");
        }
    }
}
