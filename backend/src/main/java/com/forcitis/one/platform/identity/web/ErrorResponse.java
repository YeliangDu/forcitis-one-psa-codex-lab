package com.forcitis.one.platform.identity.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Error body: the stable business code; legacyLoginUrl is the "back to the old system login" link after a failed Seeyon login. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ErrorResponse(String errorCode, String legacyLoginUrl) {
}
