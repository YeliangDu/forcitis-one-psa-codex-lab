package com.forcitis.one.kernel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The declared name (architecture/system-actors.txt) an event listener acts under; not taken from the class or method name. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SystemActorName {

    String value();
}
