package com.forcitis.one.organization.workforce.web;

import java.time.LocalDate;

record AttributeLegalEntityRequest(long legalEntityId, LocalDate effectiveFrom) {
}
