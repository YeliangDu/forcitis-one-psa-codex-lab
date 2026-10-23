package com.forcitis.one.kernel;

/** How a refusal is told to the caller; the web layer maps it to the HTTP status. */
public enum ErrorKind {
    INVALID(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    TOO_LARGE(413),
    CONFLICT(409),
    PRECONDITION_FAILED(412),
    UNPROCESSABLE(422),
    PRECONDITION_REQUIRED(428),
    TOO_MANY_REQUESTS(429),
    UNAVAILABLE(503);

    private final int status;

    ErrorKind(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
