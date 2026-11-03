package com.forcitis.one.organization.workforce.api;

/**
 * Entering and withdrawing org-structure commands (ADR-0068 item 4) and the due-date step of a merge. Entering, withdrawing and previewing need
 * organization.workforce:org-structure-command:create, checked before anything about the content. There is no REST surface and no due-date job in this phase
 * (ADR-0068 item 2.5).
 */
public interface OrgStructureCommands {

    /** A command dated today or earlier takes effect in this transaction, except a merge, which only the due-date step runs; a later one stays pending with its versions already written. */
    OrgStructureCommandId enter(EnterOrgStructureCommand command);

    /** @throws com.forcitis.one.kernel.DomainException when the command is not pending, or the reason is missing or too long */
    void withdraw(WithdrawOrgStructureCommand command);

    /** The employed people of the merged unit on the day, as the merge would move them, and who joins it later and so blocks the merge (with their commands). */
    OrgMergePreview previewMerge(PreviewOrgUnitMerge preview);

    /**
     * Runs a pending merge whose effective date has come, or continues an interrupted run: one transfer per person in its own transaction, then the
     * merged unit is revoked. Not for a user: it runs as the system actor of the due-date step and needs no permission.
     *
     * @throws com.forcitis.one.kernel.DomainException when the merge is not due or not pending or in progress, or a person could not be moved
     */
    void executeMerge(OrgStructureCommandId id);
}
