package com.forcitis.one.kernel;

/** An external system cannot be read right now (down, slow, refused). */
public class ExternalSystemUnavailableException extends RuntimeException {

    public ExternalSystemUnavailableException(String message) {
        super(message);
    }

    public ExternalSystemUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
