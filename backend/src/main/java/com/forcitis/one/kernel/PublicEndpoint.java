package com.forcitis.one.kernel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The only way to expose a handler without @PreAuthorize; the reason must cite a reviewed decision (e.g. SEC-001). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicEndpoint {
    String reason();
}
