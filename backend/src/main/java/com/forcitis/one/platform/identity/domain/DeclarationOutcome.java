package com.forcitis.one.platform.identity.domain;

/** What the start-up reconciliation found for one declared system role (ADR-0034 decision 1 item 7, ADR-0034-IMPL card row 0034-G8-01). */
public enum DeclarationOutcome {
    /** No role row or no version yet: the role and its first SEED version were written. */
    CREATED,
    /** The declaration revision is higher than the database's, or the current version is not the declared one: a new SEED version replaced it. */
    REVISED,
    /** The current SEED version holds this revision and this definition: nothing was written. */
    UNCHANGED,
    /** The database holds a higher revision than the code: an alert, nothing was written. */
    OLDER_IN_CODE,
    /** Same revision and another definition, or the role row names another identity: the start fails, nothing was written. */
    CONFLICTING
}
