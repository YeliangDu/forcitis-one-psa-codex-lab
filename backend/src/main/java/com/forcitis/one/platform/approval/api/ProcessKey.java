package com.forcitis.one.platform.approval.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The id of a deployed approval process: {subdomain}_{bc}_{name} of the business context that owns it (G47), the same text as its
 * BPMN file name (processes/{key}.bpmn20.xml) and process id. The platform lists no process; the owning context declares its own
 * constant in its api. What the process is called to a person and whether its branches need routing flags are data of the definition.
 */
@ValueObject
public record ProcessKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z]+_[a-z]+_[a-z]+(?:_[a-z]+)*");

    public ProcessKey {
        Objects.requireNonNull(value, "ProcessKey");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("ProcessKey must match " + FORMAT.pattern() + ": " + value);
        }
    }

    @JsonCreator
    public static ProcessKey of(String value) {
        return new ProcessKey(value);
    }

    public String key() {
        return value;
    }

    @JsonValue
    @Override
    public String toString() {
        return value;
    }
}
