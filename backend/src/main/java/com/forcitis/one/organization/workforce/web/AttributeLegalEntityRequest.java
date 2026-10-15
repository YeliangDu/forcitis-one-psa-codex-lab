package com.forcitis.one.organization.workforce.web;

import java.time.LocalDate;
import java.util.UUID;

record AttributeLegalEntityRequest(UUID legalEntityId, LocalDate effectiveFrom) {
}
