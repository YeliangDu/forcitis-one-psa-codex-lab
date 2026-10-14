package com.forcitis.one.kernel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A business refusal: domain exception, then ErrorCode, then ProblemDetail (platform.web). The message is for the log; what the
 * caller sees is decided by the code and the kind, plus the extensions the refusal itself names as safe to show.
 */
public class DomainException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final ErrorKind kind;
    private final Map<String, String> extensions = new LinkedHashMap<>();

    public DomainException(ErrorCode errorCode, ErrorKind kind, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorKind kind() {
        return kind;
    }

    /** A named, caller-visible property of the problem (for example a link to the old login page). */
    public DomainException withExtension(String name, String value) {
        extensions.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
        return this;
    }

    public Map<String, String> extensions() {
        return Map.copyOf(extensions);
    }
}
