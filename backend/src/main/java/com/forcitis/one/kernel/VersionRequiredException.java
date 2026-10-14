package com.forcitis.one.kernel;

/** A write that must say which version it was based on (If-Match) did not. */
public class VersionRequiredException extends RuntimeException {
    public VersionRequiredException(String message) {
        super(message);
    }
}
