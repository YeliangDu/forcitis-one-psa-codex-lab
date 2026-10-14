package com.forcitis.one.platform.identity.application;

/** What the Seeyon OA said about a ticket. */
public sealed interface SeeyonVerdict {

    /** OA resolved the ticket to this login name (already length- and character-checked, RULE-321). */
    record LoginName(String value) implements SeeyonVerdict {
    }

    /** OA does not know the ticket, or answered with something that is not a login name. */
    record Invalid() implements SeeyonVerdict {
    }

    /** OA did not answer in time, answered non-200, or is not configured. */
    record Unavailable() implements SeeyonVerdict {
    }
}
