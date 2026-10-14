package com.forcitis.one.platform.approval.domain;

public class StaleAggregateException extends RuntimeException {

    public StaleAggregateException(String message) {
        super(message);
    }
}
