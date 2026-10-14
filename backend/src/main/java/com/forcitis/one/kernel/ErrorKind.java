package com.forcitis.one.kernel;

/** How a refusal is told to the caller; the web layer maps it to the HTTP status. */
public enum ErrorKind {
    INVALID(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    UNPROCESSABLE(422),
    UNAVAILABLE(503);

    private final int status;

    ErrorKind(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
