package com.forcitis.one.platform.approval.domain;

/** Somebody else changed the aggregate between load and save; the caller retries or reports a conflict. */
public class StaleAggregateException extends RuntimeException {

    public StaleAggregateException(String message) {
        super(message);
    }
}
