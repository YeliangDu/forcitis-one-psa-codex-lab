package com.forcitis.one.platform.numbering.web;

/** templateVersion is the version the confirmer looked at; if a newer one was saved meanwhile the confirmation is refused with 409. */
record ConfirmNumberingRuleRequest(Integer templateVersion) {
}
