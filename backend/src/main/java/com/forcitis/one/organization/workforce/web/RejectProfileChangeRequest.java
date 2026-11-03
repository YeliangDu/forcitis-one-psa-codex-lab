package com.forcitis.one.organization.workforce.web;

import org.jspecify.annotations.Nullable;

/** Why the change is refused, or withdrawn by its requester: 1 to 500 characters, not blank (judged by the controller; the error does not echo the text). */
record RejectProfileChangeRequest(@Nullable String reason) {
}
