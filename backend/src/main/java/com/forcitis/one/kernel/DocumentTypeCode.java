package com.forcitis.one.kernel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.regex.Pattern;

/** The key of a document type declared by its business context; whether it is registered is the numbering registry's call. */
@ValueObject
public record DocumentTypeCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");

    public DocumentTypeCode {
        Objects.requireNonNull(value, "DocumentTypeCode");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("DocumentTypeCode must match " + FORMAT.pattern());
        }
    }

    @JsonCreator
    public static DocumentTypeCode of(String value) {
        return new DocumentTypeCode(value);
    }

    @JsonValue
    @Override
    public String toString() {
        return value;
    }
}
