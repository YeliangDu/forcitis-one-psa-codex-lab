package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/** The one content hash of the system: SHA-256 as 64 lower-case hex digits; comparisons of file content go by it alone. */
@ValueObject
public record ContentHash(String value) {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    public ContentHash {
        Objects.requireNonNull(value, "value");
        if (!SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("A content hash must be 64 lower-case hex digits");
        }
    }
}
