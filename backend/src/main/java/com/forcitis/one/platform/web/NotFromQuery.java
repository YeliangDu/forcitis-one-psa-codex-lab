package com.forcitis.one.platform.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an argument resolver of the application whose values never come from the query string, so the query parameter check skips what it resolves. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NotFromQuery {
    /** Where the values come from instead, for example the session. */
    String source();
}
