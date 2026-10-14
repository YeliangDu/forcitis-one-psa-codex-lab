package com.forcitis.one.kernel;

/** The request itself is unusable (a header or parameter that cannot be read); answered with 400, not with a state conflict. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
