package com.forcitis.one.kernel;

import java.util.Objects;
import java.util.regex.Pattern;

/** A permission identifier; catalogue membership is checked separately. */
@ValueObject
public record PermissionCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z]+\\.[a-z]+:[a-z]+(?:-[a-z]+)*:[a-z]+");

    public PermissionCode {
        Objects.requireNonNull(value, "permission code");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid permission code: " + value);
        }
    }
}
