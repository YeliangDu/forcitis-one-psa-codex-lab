package com.forcitis.one.organization.workforce.api;

/**
 * Entering and withdrawing personnel commands (ADR-0068 item 1). Both need organization.workforce:personnel-command:create, checked before anything
 * about the content. There is no REST endpoint and no due-date job in this phase (card #872); callers are other modules and the tests.
 */
public interface PersonnelCommands {

    /** A command dated today or earlier takes effect in this transaction; a later one stays pending with its versions already written. */
    PersonnelCommandId enter(EnterPersonnelCommand command);

    /** @throws com.forcitis.one.kernel.DomainException when the command is not pending or held, or the reason is missing or too long */
    void withdraw(WithdrawPersonnelCommand command);
}
