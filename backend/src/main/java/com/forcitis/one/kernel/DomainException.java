package com.forcitis.one.kernel;

import java.util.Objects;

/** A business refusal: domain exception, then ErrorCode, then ProblemDetail (platform.web). */
public class DomainException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final ErrorKind kind;

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
}
