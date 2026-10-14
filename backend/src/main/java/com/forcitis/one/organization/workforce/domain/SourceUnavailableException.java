package com.forcitis.one.organization.workforce.domain;

/** The A8 views (or the old system) cannot be read right now; the snapshot stays as it is. */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message) {
        super(message);
    }

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
