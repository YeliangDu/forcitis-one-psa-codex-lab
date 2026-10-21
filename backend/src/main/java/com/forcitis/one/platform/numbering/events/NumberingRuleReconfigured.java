package com.forcitis.one.platform.numbering.events;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import java.time.LocalDate;

/** A new version of the template was saved and waits for confirmation. */
public record NumberingRuleReconfigured(EventEnvelope envelope, DocumentTypeCode documentType, RuleVersion ruleVersion, LocalDate effectiveFrom) implements DomainEvent {
}
