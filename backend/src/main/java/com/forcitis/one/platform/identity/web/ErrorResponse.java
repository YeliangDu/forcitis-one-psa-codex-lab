package com.forcitis.one.platform.identity.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/** legacyLoginUrl is the "back to the old system login" link after a failed portal login. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ErrorResponse(String errorCode, @Nullable String legacyLoginUrl) {
}
