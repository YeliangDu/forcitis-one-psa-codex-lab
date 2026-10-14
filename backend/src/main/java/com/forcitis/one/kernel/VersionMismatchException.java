package com.forcitis.one.kernel;

/** The resource changed since the version the caller based the write on. */
public class VersionMismatchException extends RuntimeException {
    public VersionMismatchException(String message) {
        super(message);
    }
}
