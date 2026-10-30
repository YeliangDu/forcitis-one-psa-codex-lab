package com.forcitis.one.organization.workforce.web;

import org.jspecify.annotations.Nullable;

/** The reason of a withdrawal or a release; its length and blankness are judged by the use case (1 to 500 characters, not blank). */
record ReasonRequest(@Nullable String reason) {
}
