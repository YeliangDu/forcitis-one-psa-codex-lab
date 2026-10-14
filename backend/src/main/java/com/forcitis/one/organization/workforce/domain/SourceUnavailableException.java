package com.forcitis.one.organization.workforce.domain;

public class SourceUnavailableException extends RuntimeException {
    public SourceUnavailableException(String message) {
        super(message);
    }

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
