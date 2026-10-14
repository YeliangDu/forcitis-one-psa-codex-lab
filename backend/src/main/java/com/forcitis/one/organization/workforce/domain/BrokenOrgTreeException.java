package com.forcitis.one.organization.workforce.domain;

/** The A8 org tree is broken (a parent PATH is missing): the whole run aborts and nothing is written (RULE-354). */
public class BrokenOrgTreeException extends RuntimeException {

    public BrokenOrgTreeException(String message) {
        super(message);
    }
}
