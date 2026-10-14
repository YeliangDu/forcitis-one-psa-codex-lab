package com.forcitis.one.organization.workforce.web;

import org.jspecify.annotations.Nullable;

/** code is a correction; leave it out to keep the code. Leave accountingOrganizationNumber out to keep it. */
record UpdateLegalEntityRequest(@Nullable String code, String name, String status, @Nullable Long accountingOrganizationNumber) {
}
