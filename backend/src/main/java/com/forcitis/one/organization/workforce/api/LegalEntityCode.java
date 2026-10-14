package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/** The short upper-case code of a legal entity, such as FST. */
@ValueObject
public record LegalEntityCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2,10}");

    public LegalEntityCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("a legal entity code is 2 to 10 upper-case letters: " + value);
        }
    }
}
