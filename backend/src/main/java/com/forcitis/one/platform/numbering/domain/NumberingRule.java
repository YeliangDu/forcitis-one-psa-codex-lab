package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.numbering.api.ConfirmationStatus;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.events.NumberingRuleConfirmed;
import com.forcitis.one.platform.numbering.events.NumberingRuleReconfigured;
import java.util.List;
import java.util.Objects;

/** A seeded default template waits for an administrator's confirmation; saving the rule confirms it. */
public final class NumberingRule extends AggregateRoot {
    public static final StateMachine<ConfirmationStatus> MACHINE = StateMachine.of(ConfirmationStatus.class)
            .allow(ConfirmationStatus.PENDING_CONFIRMATION, ConfirmationStatus.CONFIRMED)
            .build();

    private final DocumentType documentType;
    private final ConfirmationStatus loadedConfirmation;
    private NumberingPattern pattern;
    private long startValue;
    private ConfirmationStatus confirmation;

    private NumberingRule(DocumentType documentType, NumberingPattern pattern, long startValue, ConfirmationStatus confirmation, long version) {
        super(version);
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
        this.loadedConfirmation = confirmation;
        change(pattern, startValue);
    }

    public static NumberingRule restore(DocumentType documentType, NumberingPattern pattern, long startValue, String confirmation, long version) {
        return new NumberingRule(documentType, pattern, startValue, ConfirmationStatus.valueOf(confirmation), version);
    }

    public DocumentType documentType() {
        return documentType;
    }

    public NumberingPattern pattern() {
        return pattern;
    }

    public long startValue() {
        return startValue;
    }

    public ConfirmationStatus confirmation() {
        return confirmation;
    }

    public ConfirmationStatus loadedConfirmation() {
        return loadedConfirmation;
    }

    public void confirm() {
        this.confirmation = MACHINE.require(confirmation, ConfirmationStatus.CONFIRMED, NumberingError.PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new NumberingRuleConfirmed(envelopes.wrap(draft("NumberingRuleConfirmed", version)), documentType));
    }

    /** Replaces the template; a rule still waiting for confirmation is confirmed with it. */
    public void reconfigure(NumberingPattern newPattern, long newStartValue) {
        change(newPattern, newStartValue);
        record((envelopes, version) -> new NumberingRuleReconfigured(envelopes.wrap(draft("NumberingRuleReconfigured", version)), documentType));
        if (confirmation == ConfirmationStatus.PENDING_CONFIRMATION) {
            confirm();
        }
    }

    private void change(NumberingPattern newPattern, long newStartValue) {
        Objects.requireNonNull(newPattern, "pattern");
        if (newStartValue < 1) {
            throw new IllegalArgumentException("startValue must be >= 1");
        }
        if (newPattern.usesTypeCode() && documentType.code().isEmpty()) {
            throw new IllegalArgumentException("Document type " + documentType
                    + " has no registered code, so its pattern cannot use {" + NumberingPattern.TYPE_TOKEN + "}");
        }
        this.pattern = newPattern;
        this.startValue = newStartValue;
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.numbering." + eventName, 1, "NumberingRule", documentType.name(), version, null, null,
                new EventSource(SourceSystem.ONE, null), List.of());
    }
}
