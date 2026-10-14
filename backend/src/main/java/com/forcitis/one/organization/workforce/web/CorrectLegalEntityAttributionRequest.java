package com.forcitis.one.organization.workforce.web;

import java.time.LocalDate;

record CorrectLegalEntityAttributionRequest(LocalDate periodFrom, LocalDate effectiveTo, String reason) {
}
