package com.forcitis.one.organization.workforce.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The key must be there: null clears the head, a missing key is a malformed request. */
record ChangeFinanceHeadRequest(@JsonProperty(value = "financeHeadEmployeeId", required = true) @Nullable UUID financeHeadEmployeeId) {
}
