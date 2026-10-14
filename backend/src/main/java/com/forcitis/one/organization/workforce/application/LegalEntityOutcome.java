package com.forcitis.one.organization.workforce.application;

public sealed interface LegalEntityOutcome {

    record Saved(LegalEntityView legalEntity) implements LegalEntityOutcome {
    }

    record NotFound() implements LegalEntityOutcome {
    }

    record CodeTaken() implements LegalEntityOutcome {
    }

    record Rejected(String reason) implements LegalEntityOutcome {
    }
}
