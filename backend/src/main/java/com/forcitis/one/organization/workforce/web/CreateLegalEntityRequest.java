package com.forcitis.one.organization.workforce.web;

import org.jspecify.annotations.Nullable;

/** accountingOrganizationNumber may follow later, when the accounting system has assigned it. */
record CreateLegalEntityRequest(String code, String name, @Nullable Long accountingOrganizationNumber) {
}
