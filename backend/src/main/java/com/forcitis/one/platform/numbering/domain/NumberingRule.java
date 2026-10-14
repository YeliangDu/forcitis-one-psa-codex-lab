package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.numbering.api.ConfirmationStatus;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import com.forcitis.one.platform.numbering.events.NumberingRuleConfirmed;
import com.forcitis.one.platform.numbering.events.NumberingRuleReconfigured;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A seeded default template waits for an administrator's confirmation; saving the rule confirms it. ruleVersion counts the templates
 * that have been in force: it moves when a confirmed template is replaced. effectiveFrom is the day the current template takes effect,
 * and is the businessDate of the confirmation and reconfiguration events.
 */
public final class NumberingRule extends AggregateRoot {
    public static final StateMachine<ConfirmationStatus> MACHINE = StateMachine.of(ConfirmationStatus.class)
            .allow(ConfirmationStatus.PENDING_CONFIRMATION, ConfirmationStatus.CONFIRMED)
            .build();

    private final DocumentType documentType;
    private final ConfirmationStatus loadedConfirmation;
    private NumberingPattern pattern;
    private long startValue;
    private ConfirmationStatus confirmation;
    private RuleVersion ruleVersion;
    private LocalDate effectiveFrom;

    private NumberingRule(DocumentType documentType, NumberingPattern pattern, long startValue, ConfirmationStatus confirmation,
                          long ruleVersion, LocalDate effectiveFrom, long version, long eventSequence) {
        super(version, eventSequence);
        if (confirmation == ConfirmationStatus.CONFIRMED && effectiveFrom == null) {
            throw new IllegalArgumentException("a confirmed rule has an effective day");
        }
        this.ruleVersion = new RuleVersion(ruleVersion);
        this.effectiveFrom = effectiveFrom;
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
        this.loadedConfirmation = confirmation;
        change(pattern, startValue);
    }

    /** effectiveFrom is null while the rule waits for confirmation. */
    public static NumberingRule restore(DocumentType documentType, NumberingPattern pattern, long startValue, String confirmation,
                                         long ruleVersion, LocalDate effectiveFrom, long version, long eventSequence) {
        return new NumberingRule(documentType, pattern, startValue, ConfirmationStatus.valueOf(confirmation), ruleVersion, effectiveFrom,
                version, eventSequence);
    }

    public RuleVersion ruleVersion() {
        return ruleVersion;
    }

    public LocalDate effectiveFrom() {
        return effectiveFrom;
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

    public void confirm(LocalDate from) {
        Objects.requireNonNull(from, "from");
        this.confirmation = MACHINE.require(confirmation, ConfirmationStatus.CONFIRMED, NumberingError.PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL);
        this.effectiveFrom = from;
        RuleVersion sealedRuleVersion = ruleVersion;
        record((envelopes, version) -> new NumberingRuleConfirmed(envelopes.wrap(draft("NumberingRuleConfirmed", version, from)), documentType,
                sealedRuleVersion, from));
    }

    /**
     * Replaces the template, in force from the given day; a rule still waiting for confirmation is confirmed with it. Saving the template
     * and start value it already has changes nothing, so a repeated save neither starts a new version nor moves the effective day; on a
     * rule still waiting for confirmation it only confirms.
     */
    public void reconfigure(NumberingPattern newPattern, long newStartValue, LocalDate from) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(newPattern, "pattern");
        if (newPattern.equals(pattern) && newStartValue == startValue) {
            if (confirmation == ConfirmationStatus.PENDING_CONFIRMATION) {
                confirm(from);
            }
            return;
        }
        boolean replacesTemplateInForce = confirmation == ConfirmationStatus.CONFIRMED;
        change(newPattern, newStartValue);
        if (replacesTemplateInForce) {
            this.ruleVersion = ruleVersion.next();
        }
        this.effectiveFrom = from;
        RuleVersion sealedRuleVersion = ruleVersion;
        record((envelopes, version) -> new NumberingRuleReconfigured(envelopes.wrap(draft("NumberingRuleReconfigured", version, from)), documentType,
                sealedRuleVersion, from));
        if (confirmation == ConfirmationStatus.PENDING_CONFIRMATION) {
            confirm(from);
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

    private EventDraft draft(String eventName, long version, LocalDate businessDate) {
        return new EventDraft("platform.numbering." + eventName, 1, "NumberingRule", documentType.name(), version, null, businessDate,
                new EventSource(SourceSystem.ONE, null), List.of());
    }
}
