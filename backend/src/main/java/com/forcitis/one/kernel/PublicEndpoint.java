package com.forcitis.one.kernel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The ONLY way to expose a handler without @PreAuthorize. Requires a reason that cites a reviewed decision
 * (e.g. "SSO ticket exchange, SEC-001"). Anything else is denied by default.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicEndpoint {

    String reason();
}
