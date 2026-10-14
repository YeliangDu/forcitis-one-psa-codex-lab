package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.AccountingOrganizationNumber;

public record LegalEntityAccountingNumberAssigned(EventEnvelope envelope, LegalEntityId legalEntityId,
                                                  AccountingOrganizationNumber accountingOrganizationNumber) implements DomainEvent {
}
