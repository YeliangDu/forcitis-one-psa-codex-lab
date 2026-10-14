package com.forcitis.one.kernel;

/** An external system answered, but what it returned cannot be used as a whole (for example an org tree whose parent is missing). */
public class ExternalSourceBrokenException extends RuntimeException {

    public ExternalSourceBrokenException(String message) {
        super(message);
    }
}
