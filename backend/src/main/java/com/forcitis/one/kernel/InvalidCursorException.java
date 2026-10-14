package com.forcitis.one.kernel;

/** A list request carried a cursor this server did not issue (or one from another list). */
public class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String message) {
        super(message);
    }
}
