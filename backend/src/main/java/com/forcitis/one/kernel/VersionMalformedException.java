package com.forcitis.one.kernel;

/** If-Match is there but is not the strong ETag a GET returned (weak, unquoted or not a version). */
public class VersionMalformedException extends RuntimeException {
    public VersionMalformedException(String message) {
        super(message);
    }
}
