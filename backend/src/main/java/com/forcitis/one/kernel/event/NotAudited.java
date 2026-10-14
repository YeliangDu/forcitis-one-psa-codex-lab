package com.forcitis.one.kernel.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Every domain event is written to the audit trail unless its record says why not; the AsyncAPI catalogue repeats the reason. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NotAudited {
    String reason();
}
