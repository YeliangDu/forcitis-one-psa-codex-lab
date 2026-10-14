package com.forcitis.one.platform.approval.application;

/** The caller acted on a version of the request that is no longer the current one (If-Match, 412). */
public class VersionMismatchException extends RuntimeException {

    public VersionMismatchException(String message) {
        super(message);
    }
}
