package com.forcitis.one.platform.numbering.events;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import com.forcitis.one.platform.numbering.api.ConfirmationKind;

public record NumberingRuleConfirmationGiven(EventEnvelope envelope, DocumentType documentType, RuleVersion ruleVersion, ConfirmationKind kind)
        implements DomainEvent {
}
